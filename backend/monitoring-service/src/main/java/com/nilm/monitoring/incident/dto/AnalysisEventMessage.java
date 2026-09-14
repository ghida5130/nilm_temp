package com.nilm.monitoring.incident.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisEventMessage(
        @JsonProperty("event_id") @NotNull UUID eventId,
        @JsonProperty("household_id") @NotBlank String householdId,
        @Min(0) @Max(100) int score,
        @JsonProperty("occurred_at") @NotNull Instant occurredAt,
        @NotNull JsonNode reason
) {
}
