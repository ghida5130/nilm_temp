package com.nilm.monitoring.subject.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SubjectCandidateSearchRequest(@Size(max = 100) String name, LocalDate birthDate,
        @Size(max = 50) String subjectNumber, String cursor,
        @Min(1) @Max(100) Integer size) {
}
