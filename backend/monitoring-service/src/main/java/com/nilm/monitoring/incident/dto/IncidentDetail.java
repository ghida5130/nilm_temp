package com.nilm.monitoring.incident.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record IncidentDetail(
        IncidentSummary incident,
        int score,
        JsonNode reason,
        List<ActionSummary> actions,
        List<NotificationSummary> notifications
) {
}
