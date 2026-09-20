package com.nilm.monitoring.scheduler;

import com.nilm.monitoring.config.enums.StateChangeTrigger;
import com.nilm.monitoring.service.RiskAssessmentService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기적으로 도는 위험 평가.
 *
 * <p>상태 변경만으로는 부족하다. 무활동과 루틴 미사용은 "아무 일도 일어나지 않는 것"이
 * 곧 신호이므로, 이벤트가 오지 않는 동안에도 누군가는 시계를 보고 있어야 한다.
 *
 * <p>가구마다 트랜잭션을 따로 연다. 한 가구의 실패가 나머지 가구의 평가를 막으면
 * 정작 위험한 가구를 놓칠 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.risk.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RiskAssessmentScheduler {

    private final RiskAssessmentService service;

    @Scheduled(fixedDelayString = "${app.risk.scheduler-interval-ms:60000}")
    public void evaluateAll() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Long subjectId : service.subjectIds()) {
            try {
                service.evaluateSubject(subjectId, now, StateChangeTrigger.ASSESSMENT);
            } catch (Exception e) {
                log.error("대상자 위험 평가 실패: subjectId={}", subjectId, e);
            }
        }
    }
}
