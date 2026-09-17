package com.nilm.monitoring.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
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

    protected HourlyPowerUsage() {
    }
}