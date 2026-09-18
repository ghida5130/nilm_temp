package com.nilm.monitoring.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 가구별·가전별 최근 ON/OFF 상태.
 * 스냅샷에는 전환이 아니라 현재 상태만 담겨 오므로, 직전 상태를 여기에 남겨
 * ON→OFF 전환을 가려낸다.
 */
@Entity
@Getter
@Table(name = "appliance_states")
@IdClass(ApplianceStateId.class)
public class ApplianceState {

    @Id
    @Column(name = "household_id", nullable = false)
    private String householdId;

    @Id
    @Column(name = "appliance_type", nullable = false, length = 50)
    private String applianceType;

    @Column(name = "is_on", nullable = false)
    private boolean on;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    protected ApplianceState() {
    }

    public ApplianceState(
            String householdId,
            String applianceType,
            boolean on,
            OffsetDateTime changedAt
    ) {
        this.householdId = householdId;
        this.applianceType = applianceType;
        this.on = on;
        this.changedAt = changedAt;
    }

    /**
     * 상태가 실제로 바뀐 경우에만 갱신한다.
     *
     * @return ON에서 OFF로 넘어갔으면 true
     */
    public boolean changeTo(boolean nextOn, OffsetDateTime observedAt) {
        if (this.on == nextOn) {
            return false;
        }
        boolean turnedOff = this.on && !nextOn;
        this.on = nextOn;
        this.changedAt = observedAt;
        return turnedOff;
    }
}
