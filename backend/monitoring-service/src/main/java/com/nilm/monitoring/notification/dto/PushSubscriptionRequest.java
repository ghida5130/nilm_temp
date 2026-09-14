package com.nilm.monitoring.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PushSubscriptionRequest(
        @NotBlank String endpoint,
        Long expirationTime,
        @NotNull @Valid SubscriptionKeys keys
) {
}
