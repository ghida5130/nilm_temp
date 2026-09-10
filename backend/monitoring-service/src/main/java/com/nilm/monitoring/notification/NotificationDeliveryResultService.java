package com.nilm.monitoring.notification;

import com.nilm.monitoring.incident.ActionType;
import com.nilm.monitoring.incident.ActorType;
import com.nilm.monitoring.incident.Incident;
import com.nilm.monitoring.incident.IncidentAction;
import com.nilm.monitoring.incident.IncidentActionRepository;
import com.nilm.monitoring.incident.IncidentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryResultService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentActionRepository actionRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final Duration responseTimeout;
    private final Clock clock;

    public NotificationDeliveryResultService(
            NotificationDeliveryRepository deliveryRepository,
            IncidentRepository incidentRepository,
            IncidentActionRepository actionRepository,
            PushSubscriptionRepository subscriptionRepository,
            @Value("${app.notification.response-timeout:PT30S}") Duration responseTimeout,
            Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.incidentRepository = incidentRepository;
        this.actionRepository = actionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.responseTimeout = responseTimeout;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID deliveryId, int acceptedCount, String failureReason,
                       Iterable<Long> revokedSubscriptionIds) {
        Instant now = Instant.now(clock);
        for (Long id : revokedSubscriptionIds) {
            subscriptionRepository.findById(id).ifPresent(subscription -> subscription.revoke(now));
        }

        NotificationDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null || delivery.getDeliveryStatus() != DeliveryStatus.PENDING) {
            return;
        }
        if (acceptedCount == 0) {
            delivery.markFailed(failureReason == null ? "No active push subscription" : failureReason);
            return;
        }

        delivery.markSent(now, responseTimeout, failureReason);
        Incident incident = incidentRepository.findById(delivery.getIncidentId()).orElseThrow();
        actionRepository.save(new IncidentAction(
                incident.getIncidentId(), deliveryId, ActionType.NOTIFIED,
                ActorType.SYSTEM, null, incident.getStatus(), incident.getStatus(), now));
    }
}
