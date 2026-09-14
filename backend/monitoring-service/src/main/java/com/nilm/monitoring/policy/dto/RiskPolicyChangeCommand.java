package com.nilm.monitoring.policy.dto;

import java.util.UUID;

public record RiskPolicyChangeCommand(UUID changeId, short warningThreshold,
        short dangerThreshold, int minDurationSeconds, String changeReason) {
}
