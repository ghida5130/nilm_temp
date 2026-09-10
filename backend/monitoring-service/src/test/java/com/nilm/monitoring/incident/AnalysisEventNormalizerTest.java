package com.nilm.monitoring.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.event.AnalysisEventMessage;
import com.nilm.monitoring.event.InvalidAnalysisEventException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisEventNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnalysisEventNormalizer normalizer = new AnalysisEventNormalizer("Asia/Seoul");

    @Test
    void derivesKoreanDateAndExpectedTime() throws Exception {
        AnalysisEventMessage event = new AnalysisEventMessage(
                UUID.randomUUID(), "H001", 86, Instant.parse("2026-09-02T16:30:00Z"),
                objectMapper.readTree("{\"expected_until\":\"08:10\"}"));

        AnalysisEventNormalizer.NormalizedFields result = normalizer.normalize(event);

        assertThat(result.eventDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(result.expectedUntil()).isEqualTo(LocalTime.of(8, 10));
    }

    @Test
    void rejectsMissingExpectedUntil() throws Exception {
        AnalysisEventMessage event = new AnalysisEventMessage(
                UUID.randomUUID(), "H001", 86, Instant.parse("2026-09-02T09:00:00Z"),
                objectMapper.readTree("{}"));

        assertThatThrownBy(() -> normalizer.normalize(event))
                .isInstanceOf(InvalidAnalysisEventException.class);
    }
}
