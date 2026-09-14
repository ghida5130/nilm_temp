package com.nilm.monitoring.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nilm.monitoring.domain.DeliveryStatus;
import com.nilm.monitoring.domain.NotificationDelivery;
import com.nilm.monitoring.domain.PushSubscription;
import com.nilm.monitoring.notification.repository.NotificationDeliveryRepository;
import com.nilm.monitoring.notification.repository.PushSubscriptionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationDeliveryWorker {

    private final NotificationDeliveryRepository deliveryRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final WebPushClient webPushClient;
    private final NotificationDeliveryResultService resultService;
    private final ObjectMapper objectMapper;
    private final Duration responseTimeout;
    private final Clock clock;

    public NotificationDeliveryWorker(
            NotificationDeliveryRepository deliveryRepository,
            PushSubscriptionRepository subscriptionRepository,
            WebPushClient webPushClient,
            NotificationDeliveryResultService resultService,
            ObjectMapper objectMapper,
            @Value("${app.notification.response-timeout:PT30S}") Duration responseTimeout,
            Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.webPushClient = webPushClient;
        this.resultService = resultService;
        this.objectMapper = objectMapper;
        this.responseTimeout = responseTimeout;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.notification.dispatch-delay-ms:2000}")
    public void dispatch() {
        List<UUID> ids = deliveryRepository
                .findByDeliveryStatusOrderByCreatedAt(DeliveryStatus.PENDING, PageRequest.of(0, 50))
                .stream().map(NotificationDelivery::getId).toList();
        ids.forEach(this::dispatchOne);
    }

    private void dispatchOne(UUID deliveryId) {
        NotificationDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null || delivery.getDeliveryStatus() != DeliveryStatus.PENDING) {
            return;
        }

        Instant now = Instant.now(clock);
        Map<Long, PushSubscription> subscriptions = new LinkedHashMap<>();
        List<String> recipient = List.of(delivery.getRecipientUserId());
        subscriptionRepository
                .findByUserIdInAndRevokedAtIsNullAndExpirationTimeIsNull(recipient)
                .forEach(subscription -> subscriptions.put(subscription.getSubscriptionId(), subscription));
        subscriptionRepository
                .findByUserIdInAndRevokedAtIsNullAndExpirationTimeAfter(recipient, now)
                .forEach(subscription -> subscriptions.put(subscription.getSubscriptionId(), subscription));

        ObjectNode payload = delivery.getPayload().deepCopy();
        payload.put("expiresAt", now.plus(responseTimeout).toString());
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            resultService.record(deliveryId, 0, "Cannot serialize push payload", List.of());
            return;
        }

        int accepted = 0;
        List<Long> revokedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (PushSubscription subscription : subscriptions.values()) {
            WebPushClient.PushResult result = webPushClient.send(subscription, bytes);
            if (result.accepted()) {
                accepted++;
            } else {
                errors.add("%s:%s".formatted(subscription.getSubscriptionId(), result.error()));
                if (result.subscriptionGone()) {
                    revokedIds.add(subscription.getSubscriptionId());
                }
            }
        }
        String failureReason = errors.isEmpty() ? null : String.join("; ", errors);
        if (failureReason != null && failureReason.length() > 2000) {
            failureReason = failureReason.substring(0, 2000);
        }
        resultService.record(deliveryId, accepted, failureReason, revokedIds);
    }
}
