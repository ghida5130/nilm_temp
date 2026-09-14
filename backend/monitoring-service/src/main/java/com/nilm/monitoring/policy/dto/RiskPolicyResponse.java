package com.nilm.monitoring.policy.dto;

import com.nilm.monitoring.domain.RiskPolicy;

public record RiskPolicyResponse(String id, String policyCode, String name, int version,
        String algorithmType, Short warningThreshold, Short dangerThreshold,
        int minDurationSeconds) {
    public static RiskPolicyResponse from(RiskPolicy policy) {
        return new RiskPolicyResponse(policy.getId().toString(), policy.getPolicyCode(),
                policy.getPolicyName(), policy.getVersion(), policy.getAlgorithmType(),
                policy.getWarningThreshold(), policy.getDangerThreshold(), policy.getMinDurationSeconds());
    }
}
