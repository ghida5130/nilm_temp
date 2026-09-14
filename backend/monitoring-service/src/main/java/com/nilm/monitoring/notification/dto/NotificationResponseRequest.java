package com.nilm.monitoring.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public record NotificationResponseRequest(
        @NotBlank @Pattern(regexp = "yes|no") String answer,
        @NotBlank @Pattern(regexp = "user") String source,
        @NotNull Instant respondedAt
) {
}
