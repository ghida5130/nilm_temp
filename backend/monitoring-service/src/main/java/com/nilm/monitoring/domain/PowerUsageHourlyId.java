package com.nilm.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class PowerUsageHourlyId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "household_id", nullable = false, length = 50)
    private String householdId;

    @Column(name = "hour_start", nullable = false)
    private Instant hourStart;

    protected PowerUsageHourlyId() {
    }

    public PowerUsageHourlyId(String householdId, Instant hourStart) {
        this.householdId = DomainChecks.text(householdId, "householdId", 50);
        this.hourStart = DomainChecks.required(hourStart, "hourStart");
        if (hourStart.getEpochSecond() % 3600 != 0 || hourStart.getNano() != 0) {
            throw new IllegalArgumentException("hourStart must be aligned to a UTC hour boundary");
        }
    }

    public String getHouseholdId() { return householdId; }
    public Instant getHourStart() { return hourStart; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PowerUsageHourlyId that)) {
            return false;
        }
        return Objects.equals(householdId, that.householdId)
                && Objects.equals(hourStart, that.hourStart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(householdId, hourStart);
    }
}
