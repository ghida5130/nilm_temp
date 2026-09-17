package com.nilm.monitoring.dto;

import java.time.OffsetDateTime;

public record MyDashboardResponse(
        String subjectId,
        String name,
        AwayMode awayMode,
        ManagerSummary manager
) {

    public record AwayMode(
            boolean enabled,
            OffsetDateTime startedAt,
            OffsetDateTime until
    ) {
    }

    public record ManagerSummary(
            String name,
            String phone
    ) {
    }
}
