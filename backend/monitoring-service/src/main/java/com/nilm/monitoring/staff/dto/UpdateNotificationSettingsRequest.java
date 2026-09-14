package com.nilm.monitoring.staff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateNotificationSettingsRequest(
        @NotNull Boolean dangerEnabled,
        @NotNull Boolean warningEnabled,
        @NotNull Boolean dailySummaryEnabled,
        @NotNull Boolean soundEnabled,
        @NotNull @Pattern(regexp = "[0-9]+") String expectedRevision) {
}
