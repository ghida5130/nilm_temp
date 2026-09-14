package com.nilm.monitoring.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String status,
        Instant respondedAt,
        String responseSource
) {
}
