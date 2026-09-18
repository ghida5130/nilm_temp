package com.nilm.monitoring.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Getter
@Table(name = "hourly_power_usage")
@IdClass(HourlyPowerUsageId.class)
public class HourlyPowerUsage {

    @Id
    @Column(name = "household_id", nullable = false)
    private String householdId;

    @Id
    @Column(name = "bucket_start_at", nullable = false)
    private OffsetDateTime bucketStartAt;

    @Column(
            name = "energy_wh",
            nullable = false,
            precision = 18,
            scale = 6
    )
    private BigDecimal energyWh; // 1시간동안 전력량

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt; // 이 구간 집계가 마지막으로 반영된 시각

    protected HourlyPowerUsage() {
    }

    public HourlyPowerUsage(
            String householdId,
            OffsetDateTime bucketStartAt,
            BigDecimal energyWh,
            OffsetDateTime updatedAt
    ) {
        this.householdId = householdId;
        this.bucketStartAt = bucketStartAt;
        this.energyWh = energyWh;
        this.updatedAt = updatedAt;
    }
}
