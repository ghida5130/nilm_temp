package com.nilm.monitoring.subject.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAssignmentRequest(@NotNull @Size(max = 2000) String memo,
        @NotNull @Pattern(regexp = "[0-9]+") String expectedRevision) {
}
