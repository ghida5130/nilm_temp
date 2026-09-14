package com.nilm.monitoring.staff.dto;

import com.nilm.monitoring.domain.StaffSetting;
import java.time.Instant;

public record NotificationSettingsResponse(boolean dangerEnabled, boolean warningEnabled,
        boolean dailySummaryEnabled, String dailySummaryTime, String timezone,
        boolean soundEnabled, String revision, Instant serverTime) {
    public static NotificationSettingsResponse from(StaffSetting setting, Instant now) {
        return new NotificationSettingsResponse(setting.isDangerEnabled(), setting.isWarningEnabled(),
                setting.isDailySummaryEnabled(), "09:00", "Asia/Seoul", setting.isSoundEnabled(),
                Long.toString(setting.getRevision() + 1), now);
    }
}
