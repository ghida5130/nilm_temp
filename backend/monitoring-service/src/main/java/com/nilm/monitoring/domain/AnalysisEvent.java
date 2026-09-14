package com.nilm.monitoring.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analysis_event")
public class AnalysisEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID eventId;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "household_id", nullable = false, length = 50)
    private String householdId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "appliance_type", length = 50)
    private String applianceType;

    @Column(nullable = false)
    private short score;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode reason;

    @Column(name = "risk_policy_id")
    private Long riskPolicyId;

    @Column(name = "policy_version")
    private Integer policyVersion;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected AnalysisEvent() {
    }

    public AnalysisEvent(UUID eventId, String householdId, String eventType, short score,
                         Instant occurredAt, JsonNode reason, Instant receivedAt) {
        this(eventId, null, householdId, eventType, null, score, null,
                occurredAt, reason, null, null, null, receivedAt);
    }

    public AnalysisEvent(UUID eventId, Long subjectId, String householdId, String eventType,
                         String applianceType, short score, RiskLevel riskLevel,
                         Instant occurredAt, JsonNode reason, Long riskPolicyId,
                         Integer policyVersion, String modelVersion, Instant receivedAt) {
        this.eventId = DomainChecks.required(eventId, "eventId");
        this.subjectId = positiveIdOrNull(subjectId, "subjectId");
        this.householdId = DomainChecks.text(householdId, "householdId", 50);
        this.eventType = DomainChecks.text(eventType, "eventType", 50);
        this.applianceType = optionalText(applianceType, "applianceType", 50);
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
        this.score = score;
        this.riskLevel = riskLevel;
        this.occurredAt = DomainChecks.required(occurredAt, "occurredAt");
        this.reason = DomainChecks.required(reason, "reason").deepCopy();
        validatePolicyReference(riskPolicyId, policyVersion);
        this.riskPolicyId = riskPolicyId;
        this.policyVersion = policyVersion;
        this.modelVersion = optionalText(modelVersion, "modelVersion", 50);
        this.receivedAt = DomainChecks.required(receivedAt, "receivedAt");
    }

    private static void validatePolicyReference(Long riskPolicyId, Integer policyVersion) {
        if ((riskPolicyId == null) != (policyVersion == null)) {
            throw new IllegalArgumentException("riskPolicyId and policyVersion must both be set or both be null");
        }
        positiveIdOrNull(riskPolicyId, "riskPolicyId");
        if (policyVersion != null && policyVersion <= 0) {
            throw new IllegalArgumentException("policyVersion must be positive");
        }
    }

    private static Long positiveIdOrNull(Long value, String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String optionalText(String value, String name, int maxLength) {
        return value == null ? null : DomainChecks.text(value, name, maxLength);
    }

    public UUID getEventId() { return eventId; }
    public Long getSubjectId() { return subjectId; }
    public String getHouseholdId() { return householdId; }
    public String getEventType() { return eventType; }
    public String getApplianceType() { return applianceType; }
    public short getScore() { return score; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public Instant getOccurredAt() { return occurredAt; }
    public JsonNode getReason() { return reason.deepCopy(); }
    public Long getRiskPolicyId() { return riskPolicyId; }
    public Integer getPolicyVersion() { return policyVersion; }
    public String getModelVersion() { return modelVersion; }
    public Instant getReceivedAt() { return receivedAt; }
}
