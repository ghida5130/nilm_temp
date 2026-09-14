package com.nilm.monitoring.subject.dto;

import java.time.Instant;

public record UpdateAssignmentResponse(String assignmentId, String memo,
                                       String revision, Instant updatedAt) {
}
