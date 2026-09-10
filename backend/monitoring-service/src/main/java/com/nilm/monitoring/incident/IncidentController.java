package com.nilm.monitoring.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.nilm.monitoring.common.ForbiddenException;
import com.nilm.monitoring.common.ResourceNotFoundException;
import com.nilm.monitoring.notification.NotificationDelivery;
import com.nilm.monitoring.notification.NotificationDeliveryRepository;
import com.nilm.monitoring.security.CurrentUserService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/incidents")
public class IncidentController {

    private final IncidentRepository incidentRepository;
    private final AnalysisEventRepository eventRepository;
    private final IncidentActionRepository actionRepository;
    private final HouseholdAccessRepository accessRepository;
    private final NotificationDeliveryRepository notificationRepository;
    private final CurrentUserService currentUserService;

    public IncidentController(
            IncidentRepository incidentRepository,
            AnalysisEventRepository eventRepository,
            IncidentActionRepository actionRepository,
            HouseholdAccessRepository accessRepository,
            NotificationDeliveryRepository notificationRepository,
            CurrentUserService currentUserService) {
        this.incidentRepository = incidentRepository;
        this.eventRepository = eventRepository;
        this.actionRepository = actionRepository;
        this.accessRepository = accessRepository;
        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<IncidentSummary> list(@RequestParam(required = false) IncidentStatus status) {
        List<Incident> incidents = currentUserService.isAdmin()
                ? incidentRepository.findAll(Sort.by(Sort.Direction.DESC, "openedAt"))
                : incidentRepository.findVisibleTo(currentUserService.userId(), status);
        return incidents.stream()
                .filter(incident -> status == null || incident.getStatus() == status)
                .map(IncidentSummary::from)
                .toList();
    }

    @GetMapping("/{incidentId}")
    public IncidentDetail detail(@PathVariable UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("사건을 찾을 수 없습니다."));
        if (!currentUserService.isAdmin()
                && !accessRepository.existsByHouseholdIdAndUserId(
                        incident.getHouseholdId(), currentUserService.userId())) {
            throw new ForbiddenException("이 사건을 조회할 권한이 없습니다.");
        }

        AnalysisEvent event = eventRepository.findById(incident.getSourceEventId())
                .orElseThrow(() -> new ResourceNotFoundException("분석 이벤트를 찾을 수 없습니다."));
        List<ActionSummary> actions = actionRepository.findByIncidentIdOrderByActionId(incidentId)
                .stream().map(ActionSummary::from).toList();
        List<NotificationSummary> notifications = notificationRepository
                .findByIncidentIdOrderByCreatedAt(incidentId).stream()
                .filter(delivery -> currentUserService.isAdmin()
                        || delivery.getRecipientUserId().equals(currentUserService.userId()))
                .map(NotificationSummary::from).toList();
        return new IncidentDetail(IncidentSummary.from(incident), event.getScore(), event.getReason(),
                actions, notifications);
    }

    public record IncidentSummary(
            UUID id,
            String householdId,
            String type,
            IncidentStatus status,
            Instant openedAt,
            Instant closedAt
    ) {
        static IncidentSummary from(Incident incident) {
            return new IncidentSummary(
                    incident.getIncidentId(), incident.getHouseholdId(), incident.getIncidentType(),
                    incident.getStatus(), incident.getOpenedAt(), incident.getClosedAt());
        }
    }

    public record IncidentDetail(
            IncidentSummary incident,
            int score,
            JsonNode reason,
            List<ActionSummary> actions,
            List<NotificationSummary> notifications
    ) {
    }

    public record ActionSummary(
            long id,
            ActionType type,
            ActorType actorType,
            String actorId,
            IncidentStatus previousStatus,
            IncidentStatus nextStatus,
            Instant occurredAt
    ) {
        static ActionSummary from(IncidentAction action) {
            return new ActionSummary(
                    action.getActionId(), action.getActionType(), action.getActorType(), action.getActorId(),
                    action.getPreviousStatus(), action.getNextStatus(), action.getOccurredAt());
        }
    }

    public record NotificationSummary(
            UUID id,
            String recipientUserId,
            String deliveryStatus,
            String answer,
            Instant sentAt,
            Instant responseDeadlineAt,
            Instant respondedAt,
            String responseSource
    ) {
        static NotificationSummary from(NotificationDelivery notification) {
            return new NotificationSummary(
                    notification.getId(), notification.getRecipientUserId(),
                    notification.getDeliveryStatus().name(), notification.getAnswer(), notification.getSentAt(),
                    notification.getResponseDeadlineAt(), notification.getRespondedAt(),
                    notification.getResponseSource());
        }
    }
}
