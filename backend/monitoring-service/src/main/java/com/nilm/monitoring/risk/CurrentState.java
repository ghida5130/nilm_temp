package com.nilm.monitoring.risk;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 평가 한 번이 보는 "지금"의 값 한 벌.
 *
 * <p>프로필과 마찬가지로 평가 시작 시점에 읽어 값으로 고정한다.
 * 평가 도중 새 스냅샷이 들어와도 이미 시작한 평가의 근거는 바뀌지 않는다.
 *
 * @param now 평가를 시작한 시각. 비교 기준 시각은 여기서 {@link EvaluationPoint}가 뽑는다
 * @param observation 관측 신선도와 커버리지. "보지 못한 것"을 "없었던 것"으로 바꾸지 않는 근거다
 * @param activity Gold와 같은 정의로 센 사용 사실
 */
public record CurrentState(
        OffsetDateTime now,
        boolean away,
        ObservationQuality observation,
        ActivityLedger activity
) {

    /**
     * 옛 입력 계약으로 만드는 상태.
     *
     * <p>{@code RiskAssessmentService.currentState()}가 아직 이 생성자를 부른다. 그 경로의
     * 입력으로는 관측 커버리지도, 유효 사용 정의도 확인할 수 없어서 루틴 미사용(M)과
     * 활동 감소(A)는 계산되지 않고 제외된다. 통합 단계에서 호출부를
     * {@code CurrentStateProvider.of(...)}로 바꾸면 세 지표가 모두 살아난다.
     *
     * @deprecated {@code CurrentStateProvider}가 만든 상태를 쓴다
     */
    @Deprecated(since = "monitoring-score-v1-MIA")
    public CurrentState(
            OffsetDateTime now,
            boolean away,
            OffsetDateTime lastObservedAt,
            OffsetDateTime lastActivityEndedAt,
            boolean anyApplianceOn,
            Map<String, DailyUsage> todayUsage
    ) {
        this(
                now,
                away,
                ObservationQuality.untracked(lastObservedAt),
                ActivityLedger.coarse(lastActivityEndedAt, anyApplianceOn, todayUsage));
    }

    /** 평가 이력에 남기는 현재 입력의 기준 시각. */
    public OffsetDateTime lastObservedAt() {
        return observation.lastObservedAt();
    }

    /**
     * 옛 입력의 당일 사용 사실.
     *
     * @param firstOnAt 오늘 처음 켜진 시각. 아직 안 켰으면 null
     * @param startCount 오늘 OFF→ON 전환 횟수
     * @deprecated 유효 사용 정의를 통과하지 않은 숫자다. {@link ActivityLedger.Use}를 쓴다
     */
    @Deprecated(since = "monitoring-score-v1-MIA")
    public record DailyUsage(OffsetDateTime firstOnAt, int startCount) {
    }
}
