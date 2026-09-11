package com.nilm.monitoring.incident.dto;

import com.nilm.monitoring.domain.Incident;
import com.nilm.monitoring.domain.IncidentStatus;
import java.time.Instant;
import java.util.UUID;

public record IncidentSummary(
        UUID id,
        String householdId,
        String type,
        IncidentStatus status,
        Instant openedAt,
        Instant closedAt
) {
    public static IncidentSummary from(Incident incident) {
        return new IncidentSummary(
                incident.getIncidentId(), incident.getHouseholdId(), incident.getIncidentType(),
                incident.getStatus(), incident.getOpenedAt(), incident.getClosedAt());
    }
}
