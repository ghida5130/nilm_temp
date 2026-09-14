package com.nilm.monitoring.staff.dto;

import com.nilm.monitoring.domain.StaffProfile;
import java.time.Instant;

public record StaffProfileResponse(String staffId, String displayName, String organizationName,
                                   String email, String status, String revision, Instant serverTime) {
    public static StaffProfileResponse from(StaffProfile profile, String email, Instant now) {
        return new StaffProfileResponse(profile.getId().toString(), profile.getDisplayName(),
                profile.getOrganizationName(), email, profile.getStatus().name(),
                Long.toString(profile.getRevision() + 1), now);
    }
}
