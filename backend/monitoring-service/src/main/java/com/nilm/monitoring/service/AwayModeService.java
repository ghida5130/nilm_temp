package com.nilm.monitoring.service;

import com.nilm.monitoring.config.enums.StateChangeTrigger;
import com.nilm.monitoring.domain.Subject;
import com.nilm.monitoring.dto.AwayModeRequest;
import com.nilm.monitoring.repository.SubjectRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 대상자 본인이 설정하는 외출 모드.
 *
 * <p>진실의 원천은 {@code away_started_at}~{@code away_until} 구간이고,
 * {@code monitoring_enabled}는 그 구간을 현재 시각으로 접은 캐시다.
 * 예약된 시작·종료는 {@link #applyScheduledTransitions()}가 발효시킨다.
 */
@Service
@RequiredArgsConstructor
public class AwayModeService {

    private final SubjectRepository subjects;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public void updateAwayMode(
            String subjectAuthSub,
            AwayModeRequest request
    ) {
        if (subjectAuthSub == null || subjectAuthSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        Subject subject = subjects.findByAuthSub(subjectAuthSub)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "대상자 정보를 찾을 수 없습니다."
                ));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean changed;
        try {
            if (Boolean.TRUE.equals(request.enabled())) {
                subject.scheduleAway(now, request.startsAt(), request.endsAt());
                changed = true;
            } else {
                if (request.hasSchedule()) {
                    throw new IllegalArgumentException(
                            "외출 모드를 해제할 때는 시간을 지정할 수 없습니다."
                    );
                }
                changed = subject.getAwayStartedAt() != null;
                subject.cancelAway(now);
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        if (changed) {
            publisher.publishEvent(
                    new SubjectStateChanged(subject.getId(), StateChangeTrigger.AWAY_MODE));
        }
    }

    /**
     * 예약된 외출의 시작·종료를 캐시에 반영한다.
     *
     * <p>이벤트가 들어오지 않아도 담당자 대시보드가 갱신되도록 주기적으로 돈다.
     * 값이 실제로 바뀐 대상자만 실시간 스트림으로 내보낸다.
     */
    @Transactional
    public int applyScheduledTransitions() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int changed = 0;

        for (Subject subject : subjects.findAwayTransitionCandidates(now)) {
            if (subject.syncMonitoring(now)) {
                publisher.publishEvent(
                        new SubjectStateChanged(subject.getId(), StateChangeTrigger.AWAY_MODE));
                changed++;
            }
        }
        return changed;
    }
}
