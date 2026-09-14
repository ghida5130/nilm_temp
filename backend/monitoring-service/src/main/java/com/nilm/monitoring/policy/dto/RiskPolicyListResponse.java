package com.nilm.monitoring.policy.dto;

import java.time.Instant;
import java.util.List;

public record RiskPolicyListResponse(List<RiskPolicyResponse> items, Instant serverTime) {
}
