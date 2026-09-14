package com.nilm.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "power_usage_hourly")
public class PowerUsageHourly {

    private static final BigDecimal ONE = BigDecimal.ONE;

    @EmbeddedId
    private PowerUsageHourlyId id;

    @Column(name = "energy_wh", nullable = false, precision = 14, scale = 3)
    private BigDecimal energyWh;

    @Column(name = "avg_active_power_w", nullable = false, precision = 12, scale = 3)
    private BigDecimal avgActivePowerW;

    @Column(name = "max_active_power_w", nullable = false, precision = 12, scale = 3)
    private BigDecimal maxActivePowerW;

    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount;

    @Column(name = "coverage_ratio", nullable = false, precision = 5, scale = 4)
    private BigDecimal coverageRatio;

    @Column(name = "aggregation_version", nullable = false, length = 30)
    private String aggregationVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PowerUsageHourly() {
    }

    public PowerUsageHourly(PowerUsageHourlyId id, BigDecimal energyWh,
                            BigDecimal avgActivePowerW, BigDecimal maxActivePowerW,
                            Integer sampleCount, BigDecimal coverageRatio,
                            String aggregationVersion, Instant updatedAt) {
        this.id = DomainChecks.required(id, "id");
        this.energyWh = nonNegative(energyWh, "energyWh");
        this.avgActivePowerW = nonNegative(avgActivePowerW, "avgActivePowerW");
        this.maxActivePowerW = nonNegative(maxActivePowerW, "maxActivePowerW");
        if (this.maxActivePowerW.compareTo(this.avgActivePowerW) < 0) {
            throw new IllegalArgumentException("maxActivePowerW must not be less than avgActivePowerW");
        }
        this.sampleCount = nonNegative(sampleCount, "sampleCount");
        this.coverageRatio = ratio(coverageRatio);
        this.aggregationVersion = DomainChecks.text(aggregationVersion, "aggregationVersion", 30);
        this.updatedAt = DomainChecks.required(updatedAt, "updatedAt");
    }

    public boolean hasData() {
        return sampleCount > 0;
    }

    private static BigDecimal nonNegative(BigDecimal value, String name) {
        DomainChecks.required(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static Integer nonNegative(Integer value, String name) {
        DomainChecks.required(value, name);
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static BigDecimal ratio(BigDecimal value) {
        DomainChecks.required(value, "coverageRatio");
        if (value.signum() < 0 || value.compareTo(ONE) > 0) {
            throw new IllegalArgumentException("coverageRatio must be between 0 and 1");
        }
        return value;
    }

    public PowerUsageHourlyId getId() { return id; }
    public BigDecimal getEnergyWh() { return energyWh; }
    public BigDecimal getAvgActivePowerW() { return avgActivePowerW; }
    public BigDecimal getMaxActivePowerW() { return maxActivePowerW; }
    public Integer getSampleCount() { return sampleCount; }
    public BigDecimal getCoverageRatio() { return coverageRatio; }
    public String getAggregationVersion() { return aggregationVersion; }
    public Instant getUpdatedAt() { return updatedAt; }
}
