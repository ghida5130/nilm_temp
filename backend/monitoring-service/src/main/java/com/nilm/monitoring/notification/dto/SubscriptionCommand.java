package com.nilm.monitoring.notification.dto;

import java.time.Instant;

public record SubscriptionCommand(
        String endpoint,
        String p256dh,
        String auth,
        Instant expirationTime
) {
}
