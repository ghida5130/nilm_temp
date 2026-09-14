package com.nilm.monitoring.snapshot.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ApplianceSnapshot(String applianceType, boolean isOn, BigDecimal probability,
                                BigDecimal threshold, Instant confirmedAt) {
}
