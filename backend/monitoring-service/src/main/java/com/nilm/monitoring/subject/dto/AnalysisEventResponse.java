package com.nilm.monitoring.subject.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record AnalysisEventResponse(UUID eventId, UUID incidentId, Instant occurredAt,
        String eventType, int score, String riskLevel, String applianceType,
        String description, JsonNode reason) {
}
