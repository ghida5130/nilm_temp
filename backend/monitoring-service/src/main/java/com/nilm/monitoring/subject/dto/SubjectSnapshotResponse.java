package com.nilm.monitoring.subject.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.nilm.monitoring.snapshot.dto.ApplianceSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubjectSnapshotResponse(String subjectId, String householdId, String snapshotStatus,
        String revision, Instant observedAt, Instant expiresAt, Integer riskScore, String riskLevel,
        String dataStatus, BigDecimal activePowerW, Instant lastActivityAt,
        List<ApplianceSnapshot> appliances, LatestDetection latestDetection, Instant serverTime) {
    public record LatestDetection(UUID eventId, String eventType, Instant occurredAt,
                                  String description) {}
}
