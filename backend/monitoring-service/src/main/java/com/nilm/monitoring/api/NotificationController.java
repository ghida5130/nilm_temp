package com.nilm.monitoring.api;

import com.nilm.monitoring.dto.NotificationResponseDto;
import com.nilm.monitoring.dto.NotificationResponseRequest;
import com.nilm.monitoring.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/monitoring/notifications")
public class NotificationController {

    private final NotificationService service;

    @PutMapping("/{notificationId}/responses")
    public NotificationResponseDto respond(
            @PathVariable("notificationId") Long notificationId,
            @Valid @RequestBody NotificationResponseRequest request
    ) {
        return service.respond(notificationId, request);
    }
}