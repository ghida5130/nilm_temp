package com.nilm.monitoring.api;

import com.nilm.monitoring.dto.PushSubscriptionRequest;
import com.nilm.monitoring.service.PushSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/monitoring/push-subscriptions")
public class PushSubscriptionController {

    private final PushSubscriptionService service;

    @PostMapping
    public ResponseEntity<Void> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        String authSub = jwt == null ? null : jwt.getSubject();
        boolean registered = service.register(authSub, request);

        if (!registered) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        return ResponseEntity.noContent().build();
    }
}
