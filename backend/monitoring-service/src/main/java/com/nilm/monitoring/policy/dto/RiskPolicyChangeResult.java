package com.nilm.monitoring.policy.dto;

import java.util.UUID;

public record RiskPolicyChangeResult(UUID changeId, String status, Long policyId,
                                     String failureCode) {
}
