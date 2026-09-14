package com.nilm.monitoring.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.domain.Incident;
import com.nilm.monitoring.domain.IncidentStatus;
import com.nilm.monitoring.incident.repository.IncidentActionRepository;
import com.nilm.monitoring.incident.repository.IncidentRepository;
import com.nilm.monitoring.domain.NotificationDelivery;
import com.nilm.monitoring.notification.dto.NotificationResponse;
import com.nilm.monitoring.notification.repository.NotificationDeliveryRepository;
import com.nilm.monitoring.outbox.service.OutboxWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationResponseServiceTest {

    @Test
    void userNoClosesOpenIncidentAsFalsePositive() {
        Instant sentAt = Instant.parse("2026-09-10T00:00:00Z");
        Clock clock = Clock.fixed(sentAt.plusSeconds(10), ZoneOffset.UTC);
        UUID incidentId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        Incident incident = new Incident(
                incidentId, UUID.randomUUID(), "H001", "ROUTINE_MISSED", sentAt);
        NotificationDelivery delivery = new NotificationDelivery(
                deliveryId, incidentId, "guardian-1",
                new ObjectMapper().createObjectNode(), sentAt);
        delivery.markSent(sentAt, Duration.ofSeconds(30), null);

        NotificationDeliveryRepository deliveryRepository = mock(NotificationDeliveryRepository.class);
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        IncidentActionRepository actionRepository = mock(IncidentActionRepository.class);
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        when(deliveryRepository.findByIdForUpdate(deliveryId)).thenReturn(Optional.of(delivery));
        when(incidentRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(incident));

        NotificationResponseService service = new NotificationResponseService(
                deliveryRepository, incidentRepository, actionRepository,
                outboxWriter, new ObjectMapper(), clock);

        NotificationResponse result = service.respondByUser(
                deliveryId, "guardian-1", "no", false);

        assertThat(result.status()).isEqualTo("no");
        assertThat(result.responseSource()).isEqualTo("user");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.FALSE_POSITIVE);
        verify(actionRepository).save(any());
        verify(outboxWriter).write(any(), any(), any());
    }
}
