package com.nilm.monitoring.notification;

import com.nilm.monitoring.security.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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
    public ResponseEntity<Map<String, Object>> subscribe(@Valid @RequestBody SubscriptionRequest request) {
        Instant expiration = request.expirationTime() == null
                ? null : Instant.ofEpochMilli(request.expirationTime());
        PushSubscription saved = service.subscribe(
                currentUserService.userId(),
                new PushSubscriptionService.SubscriptionCommand(
                        request.endpoint(), request.keys().p256dh(), request.keys().auth(), expiration));
        return ResponseEntity.created(URI.create(
                        "/api/monitoring/push-subscriptions/" + saved.getSubscriptionId()))
                .body(Map.of("subscriptionId", saved.getSubscriptionId()));
    }

    public record SubscriptionRequest(
            @NotBlank String endpoint,
            Long expirationTime,
            @NotNull @Valid SubscriptionKeys keys
    ) {
    }

    public record SubscriptionKeys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {
    }
}
