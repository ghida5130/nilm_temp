package com.nilm.monitoring.subject.dto;

public record SubjectCandidateResponse(String subjectId, String name, int age,
        String addressSummary, String serviceStatus, boolean alreadyAssigned) {
}
