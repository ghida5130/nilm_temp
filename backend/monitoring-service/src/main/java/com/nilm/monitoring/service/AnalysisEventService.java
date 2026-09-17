package com.nilm.monitoring.service;

import com.nilm.monitoring.config.enums.RiskLevel;
import com.nilm.monitoring.config.enums.StateChangeTrigger;
import com.nilm.monitoring.dto.kafka.AnalysisEventMessage;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.domain.AnalysisEvent;
import com.nilm.monitoring.repository.AnalysisEventRepository;
import com.nilm.monitoring.repository.SubjectRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisEventService {
    private final SubjectRepository subjects;
    private final AnalysisEventRepository events;
    private final NotificationService notifications;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;

    @Value("${app.risk.warning-threshold:70}")
    private int warningThreshold;
    @Value("${app.risk.danger-threshold:90}")
    private int dangerThreshold;
    @Value("${app.push.enabled:false}")
    private boolean pushEnabled;


    /**
     * Kakfa Consumer -> 이벤트메시지를 받아 DB 상태 갱신 후 알림 발송
     * */
    @Transactional
    public void handle(AnalysisEventMessage message) {
        validate(message);
        // 이벤트의 가구 ID에 해당하는 대상자 조회
        var matches = subjects.findHouseholdForUpdate(message.householdId());

        // 대상자가 1명이 아니면 예외
        if (matches.size() != 1) {
            throw new IllegalStateException("가구에 정확히 한 명의 대상자를 등록해야 합니다: "
                    + message.householdId());
        }

        // 이미 처리된 위험 이벤트가 있으면 중복으로 알림 처리 하지않음
        if (events.existsById(message.eventId())) return;

        var subject = matches.get(0);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // 예약된 외출의 시작/종료를 이 자리에서 반영한다.
        // 스케줄러가 늦게 돌아도 아래 알림 판정이 어긋나지 않게 한다.
        subject.syncMonitoring(now);

        // 사건이 일어난 시간에 외출 중이였는지 검사
        // 위험 발생 시각이랑 이벤트 메시지가 도착하는 시간이 다른 경우
        boolean occurredWhileAway = subject.isAwayAt(message.occurredAt());

        /**
         *  위험 스코어 계산 & 집계로 알림 조건 트리거 -> 추후 조건 확정 후 구현
         */

        RiskLevel level = calculateRiskLevel(message.score(), warningThreshold, dangerThreshold);
        subject.applyRiskAssessment(level, message.score(), now);
        String reason;
        try {
            reason = objectMapper.writeValueAsString(message.reason());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("이벤트 사유를 저장할 수 없습니다.", e);
        }


        events.saveAndFlush(new AnalysisEvent(message.eventId(), subject.getId(),
                message.householdId(), message.eventType(), message.applianceType(),
                message.score(), level, message.occurredAt(), reason, subject.getRiskPolicyId()));
        // 커밋 후 한 번만 내보낸다. 아래에서 알림이 더 만들어져도 듣는 쪽이 DB를 다시 읽는다.
        publisher.publishEvent(new SubjectStateChanged(subject.getId(), StateChangeTrigger.DETECTION));
        // 이력은 보존하고 외출/중지/정상 상태에서는 알림을 생성하지 않는다.
        if (!subject.isMonitoringEnabled() || occurredWhileAway || level == RiskLevel.NORMAL) return;
        if (subject.getAuthSub() == null || subject.getAuthSub().isBlank()) {
            log.warn("이벤트 저장 완료, 수신자 식별자 없음: subjectId={}", subject.getId());
            return;
        }
        var notification = notifications.createNotification(
                message.eventId(), subject.getAuthSub(), true);
        if (pushEnabled) {
            publisher.publishEvent(new NotificationReady(notification.getId(),
                    "안전 확인 요청", "생활 패턴 이상이 감지되었습니다. 현재 안전하신가요?"));
        }
    }

    private void validate(AnalysisEventMessage message) {
        if (warningThreshold < 0 || dangerThreshold > 100 || warningThreshold >= dangerThreshold)
            throw new IllegalStateException("위험 임계치는 0 <= 주의 < 위험 <= 100이어야 합니다.");
        if (message == null || message.eventId() == null || message.householdId() == null
                || message.householdId().isBlank() || message.score() == null
                || message.score() < 0 || message.score() > 100
                || message.occurredAt() == null || message.reason() == null)
            throw new IllegalArgumentException("분석 이벤트 필수 필드 또는 점수가 유효하지 않습니다.");
    }

    // 위험 등급 계산
    private RiskLevel calculateRiskLevel(
            int score,
            int warningThreshold,
            int dangerThreshold
    ) {
        if (score >= dangerThreshold) {
            return RiskLevel.DANGER;
        }
        if (score >= warningThreshold) {
            return RiskLevel.WARNING;
        }
        return RiskLevel.NORMAL;
    }
}
