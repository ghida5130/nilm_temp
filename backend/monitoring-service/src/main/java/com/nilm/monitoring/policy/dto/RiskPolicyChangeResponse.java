package com.nilm.monitoring.policy.dto;

import java.time.Instant;
import java.util.UUID;

public record RiskPolicyChangeResponse(UUID changeId, String status, String policyId,
        Integer policyVersion, String failureCode, Instant serverTime) {
}
