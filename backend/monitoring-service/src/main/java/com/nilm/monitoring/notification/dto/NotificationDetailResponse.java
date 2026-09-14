package com.nilm.monitoring.notification.dto;

import com.nilm.monitoring.domain.NotificationDelivery;
import java.time.Instant;
import java.util.UUID;

public record NotificationDetailResponse(UUID id, UUID incidentId, String deliveryStatus,
        Instant sentAt, Instant responseDeadlineAt, String answer, String responseSource,
        Instant respondedAt, boolean canRespond, Instant serverTime) {
    public static NotificationDetailResponse from(NotificationDelivery delivery, Instant now) {
        boolean canRespond = delivery.getDeliveryStatus().name().equals("SENT")
                && delivery.getAnswer() == null
                && delivery.getResponseDeadlineAt() != null
                && now.isBefore(delivery.getResponseDeadlineAt());
        return new NotificationDetailResponse(delivery.getId(), delivery.getIncidentId(),
                delivery.getDeliveryStatus().name(), delivery.getSentAt(),
                delivery.getResponseDeadlineAt(), delivery.getAnswer(), delivery.getResponseSource(),
                delivery.getRespondedAt(), canRespond, now);
    }
}
