package com.nilm.monitoring.notification.api;

import com.nilm.monitoring.domain.PushSubscription;
import com.nilm.monitoring.notification.dto.PushSubscriptionRequest;
import com.nilm.monitoring.notification.dto.SubscriptionCommand;
import com.nilm.monitoring.notification.service.PushSubscriptionService;
import com.nilm.monitoring.security.CurrentUserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/push-subscriptions")
public class PushSubscriptionController {

    private final PushSubscriptionService service;
    private final CurrentUserService currentUserService;

    public PushSubscriptionController(
            PushSubscriptionService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> subscribe(@Valid @RequestBody PushSubscriptionRequest request) {
        Instant expiration = request.expirationTime() == null
                ? null : Instant.ofEpochMilli(request.expirationTime());
        PushSubscription saved = service.subscribe(
                currentUserService.userId(),
                new SubscriptionCommand(
                        request.endpoint(), request.keys().p256dh(), request.keys().auth(), expiration));
        return ResponseEntity.created(URI.create(
                        "/api/monitoring/push-subscriptions/" + saved.getSubscriptionId()))
                .body(Map.of("subscriptionId", saved.getSubscriptionId()));
    }

    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> unsubscribe(@PathVariable Long subscriptionId) {
        service.revoke(subscriptionId, currentUserService.userId());
        return ResponseEntity.noContent().build();
    }

}
