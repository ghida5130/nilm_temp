package com.nilm.monitoring.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.incident.Incident;
import com.nilm.monitoring.incident.IncidentActionRepository;
import com.nilm.monitoring.incident.IncidentRepository;
import com.nilm.monitoring.incident.IncidentStatus;
import com.nilm.monitoring.outbox.OutboxWriter;
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

        NotificationResponseService.ResponseResult result = service.respondByUser(
                deliveryId, "guardian-1", "no", false);

        assertThat(result.status()).isEqualTo("no");
        assertThat(result.responseSource()).isEqualTo("user");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.FALSE_POSITIVE);
        verify(actionRepository).save(any());
        verify(outboxWriter).write(any(), any(), any());
    }
}
