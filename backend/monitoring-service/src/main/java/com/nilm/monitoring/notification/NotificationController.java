package com.nilm.monitoring.notification;

import com.nilm.monitoring.security.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
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
    public NotificationResponseService.ResponseResult respond(
            @PathVariable UUID notificationId,
            @Valid @RequestBody ResponseRequest request) {
        return responseService.respondByUser(
                notificationId, currentUserService.userId(), request.answer(), currentUserService.isAdmin());
    }

    public record ResponseRequest(
            @NotBlank @Pattern(regexp = "yes|no") String answer,
            @NotBlank @Pattern(regexp = "user") String source,
            @NotNull Instant respondedAt
    ) {
    }
}
