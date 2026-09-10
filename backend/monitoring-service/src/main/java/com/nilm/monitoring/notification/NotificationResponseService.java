package com.nilm.monitoring.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nilm.monitoring.common.ConflictException;
import com.nilm.monitoring.common.ForbiddenException;
import com.nilm.monitoring.common.ResourceNotFoundException;
import com.nilm.monitoring.incident.ActionType;
import com.nilm.monitoring.incident.ActorType;
import com.nilm.monitoring.incident.Incident;
import com.nilm.monitoring.incident.IncidentAction;
import com.nilm.monitoring.incident.IncidentActionRepository;
import com.nilm.monitoring.incident.IncidentRepository;
import com.nilm.monitoring.incident.IncidentStatus;
import com.nilm.monitoring.outbox.OutboxWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationResponseService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentActionRepository actionRepository;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationResponseService(
            NotificationDeliveryRepository deliveryRepository,
            IncidentRepository incidentRepository,
            IncidentActionRepository actionRepository,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.incidentRepository = incidentRepository;
        this.actionRepository = actionRepository;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ResponseResult respondByUser(UUID deliveryId, String userId, String requestedAnswer,
                                        boolean admin) {
        NotificationDelivery delivery = findDelivery(deliveryId);
        if (!admin && !delivery.getRecipientUserId().equals(userId)) {
            throw new ForbiddenException("이 알림에 응답할 권한이 없습니다.");
        }
        if (delivery.getAnswer() != null) {
            if (!delivery.getAnswer().equals(requestedAnswer)) {
                throw new ConflictException("알림이 다른 응답으로 이미 처리되었습니다.");
            }
            return result(delivery);
        }
        if (delivery.getDeliveryStatus() != DeliveryStatus.SENT) {
            throw new ConflictException("전송되지 않은 알림에는 응답할 수 없습니다.");
        }

        Instant now = Instant.now(clock);
        if (delivery.getResponseDeadlineAt() != null
                && !now.isBefore(delivery.getResponseDeadlineAt())) {
            return apply(delivery, "yes", ResponseSource.TIMEOUT, null, now);
        }
        return apply(delivery, requestedAnswer, ResponseSource.USER, userId, now);
    }

    @Transactional
    public ResponseResult respondByTimeout(UUID deliveryId) {
        NotificationDelivery delivery = findDelivery(deliveryId);
        if (delivery.getAnswer() != null) {
            return result(delivery);
        }
        return apply(delivery, "yes", ResponseSource.TIMEOUT, null, Instant.now(clock));
    }

    private NotificationDelivery findDelivery(UUID deliveryId) {
        return deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("알림을 찾을 수 없습니다."));
    }

    private ResponseResult apply(NotificationDelivery delivery, String answer,
                                 ResponseSource source, String actorId, Instant now) {
        Incident incident = incidentRepository.findByIdForUpdate(delivery.getIncidentId())
                .orElseThrow(() -> new ResourceNotFoundException("사건을 찾을 수 없습니다."));

        String sourceValue = source.name().toLowerCase();
        delivery.respond(answer, sourceValue, now);

        // Multiple guardians receive separate deliveries. Only the first completed
        // response changes the shared incident; later responses remain auditable on their row.
        if (incident.getStatus() != IncidentStatus.OPEN) {
            return result(delivery);
        }

        IncidentStatus nextStatus = "yes".equals(answer)
                ? IncidentStatus.CONFIRMED : IncidentStatus.FALSE_POSITIVE;
        ActionType actionType = source == ResponseSource.TIMEOUT
                ? ActionType.AUTO_CONFIRMED
                : "yes".equals(answer) ? ActionType.RESPONDED_YES : ActionType.RESPONDED_NO;

        incident.close(nextStatus, now);
        actionRepository.save(new IncidentAction(
                incident.getIncidentId(), delivery.getId(), actionType,
                source == ResponseSource.USER ? ActorType.USER : ActorType.SYSTEM,
                actorId, IncidentStatus.OPEN, nextStatus, now));

        ObjectNode payload = objectMapper.createObjectNode()
                .put("incidentId", incident.getIncidentId().toString())
                .put("notificationId", delivery.getId().toString())
                .put("householdId", incident.getHouseholdId())
                .put("status", nextStatus.name())
                .put("answer", answer)
                .put("responseSource", sourceValue)
                .put("respondedAt", now.toString());
        outboxWriter.write(incident.getHouseholdId(), "incident-updated", payload);
        return result(delivery);
    }

    private ResponseResult result(NotificationDelivery delivery) {
        return new ResponseResult(
                delivery.getId(), delivery.getAnswer(),
                delivery.getRespondedAt(), delivery.getResponseSource());
    }

    public record ResponseResult(
            UUID id,
            String status,
            Instant respondedAt,
            String responseSource
    ) {
    }
}
