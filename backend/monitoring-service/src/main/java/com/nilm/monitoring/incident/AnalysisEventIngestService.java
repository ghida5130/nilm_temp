package com.nilm.monitoring.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nilm.monitoring.event.AnalysisEventMessage;
import com.nilm.monitoring.notification.NotificationDelivery;
import com.nilm.monitoring.notification.NotificationDeliveryRepository;
import com.nilm.monitoring.outbox.OutboxWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisEventIngestService {

    private static final String EVENT_TYPE = "ROUTINE_MISSED";

    private final AnalysisEventRepository eventRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentActionRepository actionRepository;
    private final HouseholdAccessRepository accessRepository;
    private final NotificationDeliveryRepository notificationRepository;
    private final AnalysisEventNormalizer normalizer;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration maxEventAge;

    public AnalysisEventIngestService(
            AnalysisEventRepository eventRepository,
            IncidentRepository incidentRepository,
            IncidentActionRepository actionRepository,
            HouseholdAccessRepository accessRepository,
            NotificationDeliveryRepository notificationRepository,
            AnalysisEventNormalizer normalizer,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.incident.max-event-age:PT24H}") Duration maxEventAge) {
        this.eventRepository = eventRepository;
        this.incidentRepository = incidentRepository;
        this.actionRepository = actionRepository;
        this.accessRepository = accessRepository;
        this.notificationRepository = notificationRepository;
        this.normalizer = normalizer;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.maxEventAge = maxEventAge;
    }

    @Transactional
    public IngestResult ingest(AnalysisEventMessage message) {
        if (eventRepository.existsById(message.eventId())) {
            return IngestResult.DUPLICATE_EVENT;
        }

        Instant now = Instant.now(clock);
        AnalysisEventNormalizer.NormalizedFields normalized = normalizer.normalize(message);
        if (eventRepository.existsByHouseholdIdAndEventTypeAndEventDateAndExpectedUntil(
                message.householdId(), EVENT_TYPE, normalized.eventDate(), normalized.expectedUntil())) {
            return IngestResult.DUPLICATE_LOGICAL_EVENT;
        }
        eventRepository.save(new AnalysisEvent(
                message.eventId(), message.householdId(), EVENT_TYPE, (short) message.score(),
                normalized.eventDate(), normalized.expectedUntil(), message.occurredAt(),
                message.reason(), now));

        if (message.occurredAt().isBefore(now.minus(maxEventAge))) {
            return IngestResult.STORED_STALE_EVENT;
        }
        UUID incidentId = UUID.randomUUID();
        Incident incident = incidentRepository.save(new Incident(
                incidentId, message.eventId(), message.householdId(), EVENT_TYPE, message.occurredAt()));

        actionRepository.save(new IncidentAction(
                incidentId, null, ActionType.DETECTED, ActorType.SYSTEM, null,
                null, IncidentStatus.OPEN, now));

        for (String userId : accessRepository.findUserIdsByHouseholdId(message.householdId())) {
            UUID notificationId = UUID.randomUUID();
            ObjectNode notificationPayload = objectMapper.createObjectNode()
                    .put("notificationId", notificationId.toString())
                    .put("incidentId", incidentId.toString())
                    .put("householdId", message.householdId())
                    .put("title", "일상 패턴 이상이 감지되었습니다");
            notificationRepository.save(new NotificationDelivery(
                    notificationId, incidentId, userId, notificationPayload, now));
        }

        ObjectNode payload = objectMapper.createObjectNode()
                .put("incidentId", incident.getIncidentId().toString())
                .put("householdId", incident.getHouseholdId())
                .put("type", incident.getIncidentType())
                .put("score", message.score())
                .put("status", incident.getStatus().name())
                .put("openedAt", incident.getOpenedAt().toString());
        payload.set("reason", message.reason());
        outboxWriter.write(incident.getHouseholdId(), "incident-opened", payload);
        return IngestResult.CREATED;
    }

    public enum IngestResult {
        CREATED,
        DUPLICATE_EVENT,
        DUPLICATE_LOGICAL_EVENT,
        STORED_STALE_EVENT
    }
}
