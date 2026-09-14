package com.nilm.monitoring.incident.dto;

import com.nilm.monitoring.domain.NotificationDelivery;
import java.time.Instant;
import java.util.UUID;

public record NotificationSummary(
        UUID id,
        String recipientUserId,
        String deliveryStatus,
        String answer,
        Instant sentAt,
        Instant responseDeadlineAt,
        Instant respondedAt,
        String responseSource
) {
    public static NotificationSummary from(NotificationDelivery notification) {
        return new NotificationSummary(
                notification.getId(), notification.getRecipientUserId(),
                notification.getDeliveryStatus().name(), notification.getAnswer(), notification.getSentAt(),
                notification.getResponseDeadlineAt(), notification.getRespondedAt(),
                notification.getResponseSource());
    }
}
