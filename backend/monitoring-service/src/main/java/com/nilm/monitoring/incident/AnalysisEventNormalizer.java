package com.nilm.monitoring.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.nilm.monitoring.event.AnalysisEventMessage;
import com.nilm.monitoring.event.InvalidAnalysisEventException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AnalysisEventNormalizer {

    private final ZoneId zoneId;

    public AnalysisEventNormalizer(@Value("${app.incident.timezone:Asia/Seoul}") String timezone) {
        zoneId = ZoneId.of(timezone);
    }

    public NormalizedFields normalize(AnalysisEventMessage event) {
        JsonNode node = event.reason().path("expected_until");
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new InvalidAnalysisEventException("reason.expected_until is required");
        }
        try {
            return new NormalizedFields(
                    event.occurredAt().atZone(zoneId).toLocalDate(),
                    LocalTime.parse(node.asText()));
        } catch (DateTimeParseException exception) {
            throw new InvalidAnalysisEventException("reason.expected_until must be an ISO local time");
        }
    }

    public record NormalizedFields(LocalDate eventDate, LocalTime expectedUntil) {
    }
}
