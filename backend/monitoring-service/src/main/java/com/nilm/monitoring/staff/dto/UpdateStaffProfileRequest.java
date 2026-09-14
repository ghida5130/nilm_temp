package com.nilm.monitoring.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStaffProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(max = 100) String organizationName,
        @NotNull @Pattern(regexp = "[0-9]+") String expectedRevision) {
}
