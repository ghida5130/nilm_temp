package com.nilm.monitoring.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record SubscriptionKeys(
        @NotBlank String p256dh,
        @NotBlank String auth
) {
}
