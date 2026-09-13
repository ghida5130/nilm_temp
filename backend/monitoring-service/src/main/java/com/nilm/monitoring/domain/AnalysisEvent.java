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
@Table(name = "analysis_event")
public class AnalysisEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID eventId;

    @Column(name = "household_id", nullable = false, length = 50)
    private String householdId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false)
    private short score;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode reason;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected AnalysisEvent() {
    }

    public AnalysisEvent(UUID eventId, String householdId, String eventType, short score,
                         Instant occurredAt,
                         JsonNode reason, Instant receivedAt) {
        this.eventId = eventId;
        this.householdId = householdId;
        this.eventType = eventType;
        this.score = score;
        this.occurredAt = occurredAt;
        this.reason = reason;
        this.receivedAt = receivedAt;
    }

    public UUID getEventId() { return eventId; }
    public String getHouseholdId() { return householdId; }
    public String getEventType() { return eventType; }
    public short getScore() { return score; }
    public Instant getOccurredAt() { return occurredAt; }
    public JsonNode getReason() { return reason; }
    public Instant getReceivedAt() { return receivedAt; }
}
