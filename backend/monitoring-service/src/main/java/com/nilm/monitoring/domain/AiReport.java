package com.nilm.monitoring.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_report")
public class AiReport {

    @Id
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_data", nullable = false, columnDefinition = "jsonb")
    private JsonNode summaryData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_event_ids", nullable = false, columnDefinition = "jsonb")
    private JsonNode evidenceEventIds;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected AiReport() {
    }

    public AiReport(UUID id, Long staffId, Instant periodStart, Instant periodEnd,
                    String title, String content, JsonNode summaryData,
                    JsonNode evidenceEventIds, String modelVersion, Instant generatedAt) {
        this.id = DomainChecks.required(id, "id");
        this.staffId = positiveId(staffId);
        this.periodStart = DomainChecks.required(periodStart, "periodStart");
        this.periodEnd = DomainChecks.required(periodEnd, "periodEnd");
        if (!periodStart.isBefore(periodEnd)) {
            throw new IllegalArgumentException("periodStart must be before periodEnd");
        }
        this.title = DomainChecks.text(title, "title", 200);
        this.content = DomainChecks.text(content, "content", Integer.MAX_VALUE);
        this.summaryData = DomainChecks.required(summaryData, "summaryData").deepCopy();
        this.evidenceEventIds = DomainChecks.required(evidenceEventIds, "evidenceEventIds").deepCopy();
        this.modelVersion = DomainChecks.text(modelVersion, "modelVersion", 100);
        this.generatedAt = DomainChecks.required(generatedAt, "generatedAt");
    }

    public void confirm(Instant now) {
        DomainChecks.required(now, "now");
        if (now.isBefore(generatedAt)) {
            throw new IllegalArgumentException("confirmedAt must not be before generatedAt");
        }
        if (confirmedAt == null) {
            confirmedAt = now;
        }
    }

    private static Long positiveId(Long value) {
        DomainChecks.required(value, "staffId");
        if (value <= 0) {
            throw new IllegalArgumentException("staffId must be positive");
        }
        return value;
    }

    public UUID getId() { return id; }
    public Long getStaffId() { return staffId; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public JsonNode getSummaryData() { return summaryData.deepCopy(); }
    public JsonNode getEvidenceEventIds() { return evidenceEventIds.deepCopy(); }
    public String getModelVersion() { return modelVersion; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
