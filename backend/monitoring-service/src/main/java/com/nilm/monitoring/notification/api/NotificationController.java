package com.nilm.monitoring.notification.api;

import com.nilm.monitoring.notification.dto.NotificationResponse;
import com.nilm.monitoring.notification.dto.NotificationResponseRequest;
import com.nilm.monitoring.notification.service.NotificationResponseService;
import com.nilm.monitoring.notification.service.NotificationQueryService;
import com.nilm.monitoring.notification.dto.NotificationDetailResponse;
import com.nilm.monitoring.security.CurrentUserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/monitoring/notifications")
public class NotificationController {

    private final NotificationResponseService responseService;
    private final CurrentUserService currentUserService;
    private final NotificationQueryService queryService;

    public NotificationController(
            NotificationResponseService responseService,
            NotificationQueryService queryService,
            CurrentUserService currentUserService) {
        this.responseService = responseService;
        this.queryService = queryService;
        this.currentUserService = currentUserService;
    }

    @PutMapping("/{notificationId}/responses")
    public NotificationResponse respond(
            @PathVariable UUID notificationId,
            @Valid @RequestBody NotificationResponseRequest request) {
        return responseService.respondByUser(
                notificationId, currentUserService.userId(), request.answer(), currentUserService.isAdmin());
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationDetailResponse> get(@PathVariable UUID notificationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(queryService.get(notificationId, currentUserService.userId()));
    }
}
