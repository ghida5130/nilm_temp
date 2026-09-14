package com.nilm.monitoring.subject.dto;

import java.time.Instant;

public record SubjectCardResponse(String subjectId, String assignmentId, String householdId,
        String name, int age, String addressSummary, String serviceStatus, Integer riskScore,
        String riskLevel, String snapshotStatus, Instant observedAt, Instant lastActivityAt,
        long openIncidentCount) {
}
