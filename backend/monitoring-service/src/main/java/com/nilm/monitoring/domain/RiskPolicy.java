package com.nilm.monitoring.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "risk_policy")
public class RiskPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_code", nullable = false, length = 50)
    private String policyCode;

    @Column(name = "policy_name", nullable = false, length = 100)
    private String policyName;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "algorithm_type", nullable = false, length = 50)
    private String algorithmType;

    @Column(name = "warning_threshold")
    private Short warningThreshold;

    @Column(name = "danger_threshold")
    private Short dangerThreshold;

    @Column(name = "min_duration_seconds", nullable = false)
    private Integer minDurationSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode parameters;

    @Column(name = "change_reason", nullable = false, columnDefinition = "text")
    private String changeReason;

    @Column(name = "created_by_staff_id", nullable = false)
    private Long createdByStaffId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RiskPolicy() {
    }

    private RiskPolicy(String policyCode, String policyName, Integer version,
                       String algorithmType, Short warningThreshold, Short dangerThreshold,
                       Integer minDurationSeconds, JsonNode parameters, String changeReason,
                       Long createdByStaffId, Instant createdAt) {
        this.policyCode = DomainChecks.text(policyCode, "policyCode", 50);
        this.policyName = DomainChecks.text(policyName, "policyName", 100);
        this.version = positive(version, "version");
        this.algorithmType = DomainChecks.text(algorithmType, "algorithmType", 50);
        validateThresholds(warningThreshold, dangerThreshold);
        this.warningThreshold = warningThreshold;
        this.dangerThreshold = dangerThreshold;
        this.minDurationSeconds = nonNegative(minDurationSeconds, "minDurationSeconds");
        this.parameters = DomainChecks.required(parameters, "parameters").deepCopy();
        this.changeReason = DomainChecks.text(changeReason, "changeReason", Integer.MAX_VALUE);
        this.createdByStaffId = positive(createdByStaffId, "createdByStaffId");
        this.createdAt = DomainChecks.required(createdAt, "createdAt");
    }

    public static RiskPolicy createVersion(String policyCode, String policyName, Integer version,
                                           String algorithmType, Short warningThreshold,
                                           Short dangerThreshold, Integer minDurationSeconds,
                                           JsonNode parameters, String changeReason,
                                           Long createdByStaffId, Instant createdAt) {
        return new RiskPolicy(policyCode, policyName, version, algorithmType, warningThreshold,
                dangerThreshold, minDurationSeconds, parameters, changeReason,
                createdByStaffId, createdAt);
    }

    private static void validateThresholds(Short warning, Short danger) {
        if ((warning == null) != (danger == null)) {
            throw new IllegalArgumentException("warningThreshold and dangerThreshold must both be set or both be null");
        }
        if (warning == null) {
            return;
        }
        if (warning < 0 || warning > 100 || danger < 0 || danger > 100) {
            throw new IllegalArgumentException("thresholds must be between 0 and 100");
        }
        if (warning >= danger) {
            throw new IllegalArgumentException("warningThreshold must be less than dangerThreshold");
        }
    }

    private static <T extends Number> T positive(T value, String name) {
        DomainChecks.required(value, name);
        if (value.longValue() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Integer nonNegative(Integer value, String name) {
        DomainChecks.required(value, name);
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    public Long getId() { return id; }
    public String getPolicyCode() { return policyCode; }
    public String getPolicyName() { return policyName; }
    public Integer getVersion() { return version; }
    public String getAlgorithmType() { return algorithmType; }
    public Short getWarningThreshold() { return warningThreshold; }
    public Short getDangerThreshold() { return dangerThreshold; }
    public Integer getMinDurationSeconds() { return minDurationSeconds; }
    public JsonNode getParameters() { return parameters.deepCopy(); }
    public String getChangeReason() { return changeReason; }
    public Long getCreatedByStaffId() { return createdByStaffId; }
    public Instant getCreatedAt() { return createdAt; }
}
