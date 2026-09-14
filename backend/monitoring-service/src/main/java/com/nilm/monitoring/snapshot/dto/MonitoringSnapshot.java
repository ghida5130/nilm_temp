package com.nilm.monitoring.snapshot.dto;

import com.nilm.monitoring.domain.RiskLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MonitoringSnapshot(String householdId, Long revision, Instant observedAt,
        Instant expiresAt, BigDecimal activePowerW, Integer riskScore, RiskLevel riskLevel,
        Instant lastActivityAt, String dataStatus, List<ApplianceSnapshot> appliances) {
}
