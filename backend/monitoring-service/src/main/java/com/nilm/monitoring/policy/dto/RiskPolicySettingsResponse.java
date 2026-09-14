package com.nilm.monitoring.policy.dto;

import java.time.Instant;
import java.util.List;

public record RiskPolicySettingsResponse(String defaultPolicyId, int policyVersion,
        short warningThreshold, short dangerThreshold, int minDurationSeconds,
        List<Integer> allowedDurationSeconds, String scope, String revision, Instant serverTime) {
}
