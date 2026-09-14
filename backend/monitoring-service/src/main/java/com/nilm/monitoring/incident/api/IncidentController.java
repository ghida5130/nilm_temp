package com.nilm.monitoring.incident.api;

import com.nilm.monitoring.common.ForbiddenException;
import com.nilm.monitoring.common.ResourceNotFoundException;
import com.nilm.monitoring.domain.AnalysisEvent;
import com.nilm.monitoring.domain.Incident;
import com.nilm.monitoring.domain.IncidentStatus;
import com.nilm.monitoring.incident.dto.ActionSummary;
import com.nilm.monitoring.incident.dto.IncidentDetail;
import com.nilm.monitoring.incident.dto.IncidentSummary;
import com.nilm.monitoring.incident.dto.NotificationSummary;
import com.nilm.monitoring.incident.repository.AnalysisEventRepository;
import com.nilm.monitoring.incident.repository.HouseholdAccessRepository;
import com.nilm.monitoring.incident.repository.IncidentActionRepository;
import com.nilm.monitoring.incident.repository.IncidentRepository;
import com.nilm.monitoring.notification.repository.NotificationDeliveryRepository;
import com.nilm.monitoring.security.CurrentUserService;
import com.nilm.monitoring.subject.service.SubjectAccessService;
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
    private final SubjectAccessService subjectAccessService;

    public IncidentController(
            IncidentRepository incidentRepository,
            AnalysisEventRepository eventRepository,
            IncidentActionRepository actionRepository,
            HouseholdAccessRepository accessRepository,
            NotificationDeliveryRepository notificationRepository,
            SubjectAccessService subjectAccessService,
            CurrentUserService currentUserService) {
        this.incidentRepository = incidentRepository;
        this.eventRepository = eventRepository;
        this.actionRepository = actionRepository;
        this.accessRepository = accessRepository;
        this.notificationRepository = notificationRepository;
        this.subjectAccessService = subjectAccessService;
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
                        incident.getHouseholdId(), currentUserService.userId())
                && !subjectAccessService.canAccessHousehold(
                        currentUserService.userId(), incident.getHouseholdId())) {
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
}
