package com.nilm.monitoring.notification.dto;

public record PushConfigResponse(boolean enabled, String vapidPublicKey) {
}
