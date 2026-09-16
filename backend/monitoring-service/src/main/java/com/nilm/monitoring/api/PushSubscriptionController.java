package com.nilm.monitoring.api;

import com.nilm.monitoring.dto.PushSubscriptionRequest;
import com.nilm.monitoring.service.PushSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/push-subscriptions")
public class PushSubscriptionController {

    private final PushSubscriptionService service;
    private final String testAuthSub;

    public PushSubscriptionController(
            PushSubscriptionService service,
            @Value("${app.push.test-auth-sub}") String testAuthSub
    ) {
        this.service = service;
        this.testAuthSub = testAuthSub;
    }

    @PostMapping
    public ResponseEntity<Void> register(
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        boolean registered = service.register(testAuthSub, request);

        if (!registered) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        return ResponseEntity.noContent().build();
    }
}
