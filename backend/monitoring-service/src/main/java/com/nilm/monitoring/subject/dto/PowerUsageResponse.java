package com.nilm.monitoring.subject.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PowerUsageResponse(String subjectId, LocalDate date, String timezone, String interval,
                                 List<Point> points, Instant serverTime) {
    public record Point(Instant hourStart, BigDecimal energyWh, BigDecimal avgActivePowerW,
                        BigDecimal maxActivePowerW, Integer sampleCount, BigDecimal coverageRatio,
                        String dataStatus, String aggregationVersion) {}
}
