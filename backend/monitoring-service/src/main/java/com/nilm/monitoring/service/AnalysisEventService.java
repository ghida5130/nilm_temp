package com.nilm.monitoring.service;

import com.nilm.monitoring.config.enums.RiskLevel;
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

    @Transactional
    public void handle(AnalysisEventMessage message) {
        validate(message);
        // 같은 가구의 처리를 직렬화해 동시 재수신도 중복 알림을 만들지 않는다.
        var matches = subjects.findHouseholdForUpdate(message.householdId());
        if (matches.size() != 1) {
            throw new IllegalStateException("가구에 정확히 한 명의 대상자를 등록해야 합니다: "
                    + message.householdId());
        }
        if (events.existsById(message.eventId())) return;
        var subject = matches.get(0);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!subject.isMonitoringEnabled() && subject.getAwayStartedAt() != null
                && subject.getAwayUntil() != null && !now.isBefore(subject.getAwayUntil())) {
            subject.endAway(now);
        }
        boolean occurredWhileAway = subject.getAwayStartedAt() != null
                && subject.getAwayUntil() != null
                && !message.occurredAt().isBefore(subject.getAwayStartedAt())
                && message.occurredAt().isBefore(subject.getAwayUntil());
        RiskLevel level = calculateRiskLevel(message.score(), warningThreshold, dangerThreshold);
        String reason;
        try {
            reason = objectMapper.writeValueAsString(message.reason());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("이벤트 사유를 저장할 수 없습니다.", e);
        }
        events.saveAndFlush(new AnalysisEvent(message.eventId(), subject.getId(),
                message.householdId(), message.eventType(), message.applianceType(),
                message.score(), level, message.occurredAt(), reason, subject.getRiskPolicyId()));
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
