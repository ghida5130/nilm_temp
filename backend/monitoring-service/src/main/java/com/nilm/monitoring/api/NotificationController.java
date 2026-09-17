package com.nilm.monitoring.api;

import com.nilm.monitoring.dto.NotificationResponseDto;
import com.nilm.monitoring.dto.NotificationResponseRequest;
import com.nilm.monitoring.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/monitoring/notifications")
public class NotificationController {

    private final NotificationService service;

    @PutMapping("/{notificationId}/responses")
    public NotificationResponseDto respond(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("notificationId") Long notificationId,
            @Valid @RequestBody NotificationResponseRequest request
    ) {
        String authSub = jwt == null ? null : jwt.getSubject();
        return service.respond(notificationId, authSub, request);
    }
}
