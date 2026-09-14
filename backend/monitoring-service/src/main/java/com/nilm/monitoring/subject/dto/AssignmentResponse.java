package com.nilm.monitoring.subject.dto;

import java.time.Instant;

public record AssignmentResponse(String assignmentId, String subjectId, String riskPolicyId,
                                 String revision, Instant assignedAt) {
}
