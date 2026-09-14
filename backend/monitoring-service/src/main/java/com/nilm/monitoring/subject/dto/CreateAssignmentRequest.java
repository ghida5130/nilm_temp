package com.nilm.monitoring.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAssignmentRequest(
        @NotBlank @Pattern(regexp = "[0-9]+") String subjectId,
        @NotBlank @Pattern(regexp = "[0-9]+") String riskPolicyId,
        @Size(max = 2000) String memo) {
}
