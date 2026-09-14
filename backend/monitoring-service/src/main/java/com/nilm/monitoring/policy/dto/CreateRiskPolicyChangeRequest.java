package com.nilm.monitoring.policy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRiskPolicyChangeRequest(
        @NotNull @Min(0) @Max(100) Short warningThreshold,
        @NotNull @Min(0) @Max(100) Short dangerThreshold,
        @NotNull @Min(0) Integer minDurationSeconds,
        @NotNull @Pattern(regexp = "[0-9]+") String expectedRevision,
        @NotBlank @Size(max = 2000) String changeReason) {
}
