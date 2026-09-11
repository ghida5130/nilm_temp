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
@Table(name = "ui_outbox")
public class UiOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long outboxId;

    @Column(name = "household_id", nullable = false, length = 50)
    private String householdId;

    @Column(name = "event_name", nullable = false, length = 40)
    private String eventName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected UiOutbox() {
    }

    public UiOutbox(String householdId, String eventName, JsonNode payload, Instant createdAt) {
        this.householdId = householdId;
        this.eventName = eventName;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant now) {
        publishedAt = now;
    }

    public Long getOutboxId() { return outboxId; }
    public String getHouseholdId() { return householdId; }
    public String getEventName() { return eventName; }
    public JsonNode getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
