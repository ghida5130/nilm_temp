package com.nilm.monitoring.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;

/**
 * 가구별 마지막 관측 시각.
 *
 * <p>평가가 "지금 값"을 믿어도 되는지 판정하는 근거다. 스냅샷이 끊긴 동안의 무활동은
 * 대상자가 가만히 있었다는 뜻이 아니라 우리가 보지 못했다는 뜻이므로,
 * 이 값이 오래되면 현재 값을 쓰는 지표를 계산하지 않는다.
 *
 * <p>대상자 행과 분리해 둔다. 스냅샷은 이상 징후가 없어도 계속 들어오는데
 * 그때마다 가구 행 락을 잡으면 이벤트 처리와 불필요하게 부딪힌다.
 */
@Entity
@Getter
@Table(name = "household_observations")
public class HouseholdObservation {

    @Id
    @Column(name = "household_id")
    private String householdId;

    @Column(name = "last_observed_at", nullable = false)
    private OffsetDateTime lastObservedAt;

    @Column(name = "last_published_at")
    private OffsetDateTime lastPublishedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected HouseholdObservation() {
    }

    public HouseholdObservation(
            String householdId,
            OffsetDateTime lastObservedAt,
            OffsetDateTime lastPublishedAt,
            OffsetDateTime updatedAt
    ) {
        this.householdId = householdId;
        this.lastObservedAt = lastObservedAt;
        this.lastPublishedAt = lastPublishedAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 더 새로운 관측만 반영한다.
     * 파티션 재배치나 재전송으로 과거 스냅샷이 뒤늦게 도착해도 신선도가 되돌아가지 않는다.
     *
     * @return 값이 실제로 갱신되었으면 true
     */
    public boolean observe(
            OffsetDateTime observedAt,
            OffsetDateTime publishedAt,
            OffsetDateTime now
    ) {
        if (observedAt == null || observedAt.isBefore(lastObservedAt)) {
            return false;
        }
        this.lastObservedAt = observedAt;
        this.lastPublishedAt = publishedAt;
        this.updatedAt = now;
        return true;
    }
}
