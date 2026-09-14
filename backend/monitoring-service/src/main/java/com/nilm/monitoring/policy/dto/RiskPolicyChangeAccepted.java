package com.nilm.monitoring.policy.dto;

import java.util.UUID;

public record RiskPolicyChangeAccepted(UUID changeId, String status) {
}
