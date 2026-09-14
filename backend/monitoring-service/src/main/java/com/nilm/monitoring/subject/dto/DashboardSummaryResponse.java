package com.nilm.monitoring.subject.dto;

import java.time.Instant;

public record DashboardSummaryResponse(long totalSubjects, long dangerCount, long warningCount,
                                       long normalCount, long unknownCount, Instant serverTime) {
}
