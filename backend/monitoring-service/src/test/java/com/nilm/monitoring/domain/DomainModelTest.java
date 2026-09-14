package com.nilm.monitoring.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainModelTest {

    private static final Instant BASE_TIME = Instant.parse("2026-09-14T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void careSubjectEnforcesLifecycleAndProtectsEncryptedValues() {
        byte[] encryptedName = {1, 2, 3};
        CareSubject subject = CareSubject.register(
                "household-1", "subject-1", LocalDate.of(1950, 1, 1),
                encryptedName, new byte[]{4}, new byte[]{5}, "11", null, BASE_TIME);

        encryptedName[0] = 9;
        assertThat(subject.getName()).containsExactly(1, 2, 3);
        byte[] returnedName = subject.getName();
        returnedName[0] = 8;
        assertThat(subject.getName()).containsExactly(1, 2, 3);

        subject.activate(BASE_TIME.plusSeconds(1));
        Instant firstActivation = subject.getActivatedAt();
        subject.pause(BASE_TIME.plusSeconds(2));
        subject.resume(BASE_TIME.plusSeconds(3));

        assertThat(subject.getStatus()).isEqualTo(SubjectStatus.ACTIVE);
        assertThat(subject.getActivatedAt()).isEqualTo(firstActivation);

        subject.end(BASE_TIME.plusSeconds(4));
        assertThatThrownBy(() -> subject.resume(BASE_TIME.plusSeconds(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void analysisEventRequiresCompletePolicyReferenceAndCopiesJson() {
        ObjectNode reason = objectMapper.createObjectNode().put("expected_until", "09:00:00");
        UUID eventId = UUID.randomUUID();

        AnalysisEvent event = new AnalysisEvent(
                eventId, 1L, "household-1", "ROUTINE_MISSED", null,
                (short) 80, RiskLevel.WARNING, BASE_TIME, reason, 3L, 2, "model-1", BASE_TIME);

        reason.put("tampered", true);
        assertThat(event.getReason().has("tampered")).isFalse();
        ObjectNode returned = (ObjectNode) event.getReason();
        returned.put("tampered", true);
        assertThat(event.getReason().has("tampered")).isFalse();

        assertThatThrownBy(() -> new AnalysisEvent(
                eventId, null, "household-1", "ROUTINE_MISSED", null,
                (short) 80, null, BASE_TIME, reason, 3L, null, null, BASE_TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incidentSeparatesAcknowledgementClearanceAndResolution() {
        Incident incident = Incident.open(
                UUID.randomUUID(), UUID.randomUUID(), 1L, "household-1",
                "ROUTINE_MISSED", Severity.DANGER, BASE_TIME);

        incident.assign(10L, BASE_TIME.plusSeconds(1));
        incident.acknowledge(10L, BASE_TIME.plusSeconds(2));
        Instant acknowledgedAt = incident.getAcknowledgedAt();
        incident.acknowledge(10L, BASE_TIME.plusSeconds(3));
        assertThat(incident.getAcknowledgedAt()).isEqualTo(acknowledgedAt);

        UUID clearanceId = UUID.randomUUID();
        incident.recordClearance(clearanceId, BASE_TIME.plusSeconds(4));
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);

        incident.resolve(10L, "COMPLETED", BASE_TIME.plusSeconds(5));
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getClearedEventId()).isEqualTo(clearanceId);
        assertThatThrownBy(() -> incident.assign(11L, BASE_TIME.plusSeconds(6)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hourlyUsageValidatesUtcBoundaryAndRanges() {
        PowerUsageHourlyId id = new PowerUsageHourlyId("household-1", BASE_TIME);
        PowerUsageHourly usage = new PowerUsageHourly(
                id, new BigDecimal("100.000"), new BigDecimal("10.000"),
                new BigDecimal("20.000"), 60, new BigDecimal("0.9000"),
                "aggregation-v1", BASE_TIME);

        assertThat(usage.hasData()).isTrue();
        assertThatThrownBy(() -> new PowerUsageHourlyId(
                "household-1", BASE_TIME.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PowerUsageHourly(
                id, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1,
                new BigDecimal("1.0001"), "aggregation-v1", BASE_TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notificationPreferenceUsesExplicitSeverityOrdering() {
        NotificationPreference preference = new NotificationPreference(
                1L, "INCIDENT", NotificationChannel.WEB_PUSH,
                true, Severity.DANGER, BASE_TIME);

        assertThat(preference.accepts(Severity.WARNING)).isFalse();
        assertThat(preference.accepts(Severity.DANGER)).isTrue();
    }
}
