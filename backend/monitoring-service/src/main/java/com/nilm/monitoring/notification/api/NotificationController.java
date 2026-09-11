package com.nilm.monitoring.notification.api;

import com.nilm.monitoring.notification.dto.NotificationResponse;
import com.nilm.monitoring.notification.dto.NotificationResponseRequest;
import com.nilm.monitoring.notification.service.NotificationResponseService;
import com.nilm.monitoring.security.CurrentUserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/notifications")
public class NotificationController {

    private final NotificationResponseService responseService;
    private final CurrentUserService currentUserService;

    public NotificationController(
            NotificationResponseService responseService,
            CurrentUserService currentUserService) {
        this.responseService = responseService;
        this.currentUserService = currentUserService;
    }

    @PutMapping("/{notificationId}/responses")
    public NotificationResponse respond(
            @PathVariable UUID notificationId,
            @Valid @RequestBody NotificationResponseRequest request) {
        return responseService.respondByUser(
                notificationId, currentUserService.userId(), request.answer(), currentUserService.isAdmin());
    }
}
