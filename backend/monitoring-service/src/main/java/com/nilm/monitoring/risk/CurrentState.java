package com.nilm.monitoring.risk;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 평가 한 번이 보는 "지금"의 값 한 벌.
 *
 * <p>프로필과 마찬가지로 평가 시작 시점에 읽어 값으로 고정한다.
 * 평가 도중 새 스냅샷이 들어와도 이미 시작한 평가의 근거는 바뀌지 않는다.
 *
 * @param lastObservedAt 마지막으로 도착한 스냅샷의 관측 시각. 관측 신선도 판정에 쓴다
 * @param lastActivityEndedAt 마지막 가전 ON→OFF 전환 시각. 무활동 경과의 기준점이다
 * @param anyApplianceOn 지금 켜져 있는 가전이 하나라도 있는지
 * @param todayUsage 오늘(KST) 가전별 사용 사실
 */
public record CurrentState(
        OffsetDateTime now,
        boolean away,
        OffsetDateTime lastObservedAt,
        OffsetDateTime lastActivityEndedAt,
        boolean anyApplianceOn,
        Map<String, DailyUsage> todayUsage
) {

    /**
     * @param firstOnAt 오늘 처음 켜진 시각. 아직 안 켰으면 null
     * @param startCount 오늘 OFF→ON 전환 횟수
     */
    public record DailyUsage(OffsetDateTime firstOnAt, int startCount) {
    }
}
