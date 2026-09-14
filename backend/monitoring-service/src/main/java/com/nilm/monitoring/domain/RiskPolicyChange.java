package com.nilm.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_policy_change")
public class RiskPolicyChange {
    @Id
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "expected_revision", nullable = false)
    private Long expectedRevision;

    @Column(name = "warning_threshold", nullable = false)
    private short warningThreshold;

    @Column(name = "danger_threshold", nullable = false)
    private short dangerThreshold;

    @Column(name = "min_duration_seconds", nullable = false)
    private int minDurationSeconds;

    @Column(name = "change_reason", nullable = false, columnDefinition = "text")
    private String changeReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskPolicyChangeStatus status;

    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected RiskPolicyChange() {}

    public RiskPolicyChange(UUID id, Long staffId, Long expectedRevision, short warningThreshold,
                            short dangerThreshold, int minDurationSeconds, String changeReason, Instant now) {
        this.id = DomainChecks.required(id, "id");
        if (staffId == null || staffId <= 0) throw new IllegalArgumentException("staffId must be positive");
        if (expectedRevision == null || expectedRevision < 0) throw new IllegalArgumentException("expectedRevision must not be negative");
        if (warningThreshold < 0 || dangerThreshold > 100 || warningThreshold >= dangerThreshold) {
            throw new IllegalArgumentException("invalid thresholds");
        }
        if (minDurationSeconds < 0) throw new IllegalArgumentException("minDurationSeconds must not be negative");
        this.staffId = staffId;
        this.expectedRevision = expectedRevision;
        this.warningThreshold = warningThreshold;
        this.dangerThreshold = dangerThreshold;
        this.minDurationSeconds = minDurationSeconds;
        this.changeReason = DomainChecks.text(changeReason, "changeReason", 2000);
        this.status = RiskPolicyChangeStatus.PENDING;
        this.createdAt = DomainChecks.required(now, "now");
    }

    public void markApplied(Long policyId, Instant now) {
        if (status != RiskPolicyChangeStatus.PENDING) throw new IllegalStateException("change already completed");
        if (policyId == null || policyId <= 0) throw new IllegalArgumentException("policyId must be positive");
        this.policyId = policyId;
        this.status = RiskPolicyChangeStatus.APPLIED;
        this.completedAt = DomainChecks.required(now, "now");
    }

    public void markFailed(String failureCode, Instant now) {
        if (status != RiskPolicyChangeStatus.PENDING) throw new IllegalStateException("change already completed");
        this.failureCode = DomainChecks.text(failureCode, "failureCode", 100);
        this.status = RiskPolicyChangeStatus.FAILED;
        this.completedAt = DomainChecks.required(now, "now");
    }

    public UUID getId() { return id; }
    public Long getStaffId() { return staffId; }
    public Long getExpectedRevision() { return expectedRevision; }
    public short getWarningThreshold() { return warningThreshold; }
    public short getDangerThreshold() { return dangerThreshold; }
    public int getMinDurationSeconds() { return minDurationSeconds; }
    public String getChangeReason() { return changeReason; }
    public RiskPolicyChangeStatus getStatus() { return status; }
    public Long getPolicyId() { return policyId; }
    public String getFailureCode() { return failureCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
