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

    @Column(name = "household_id", length = 50)
    private String householdId;

    @Column(name = "recipient_user_id", length = 255)
    private String recipientUserId;

    @Column(name = "event_name", nullable = false, length = 50)
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
        this.householdId = DomainChecks.text(householdId, "householdId", 50);
        this.recipientUserId = null;
        this.eventName = DomainChecks.text(eventName, "eventName", 50);
        this.payload = DomainChecks.required(payload, "payload").deepCopy();
        this.createdAt = DomainChecks.required(createdAt, "createdAt");
    }

    public static UiOutbox personal(String recipientUserId, String eventName,
                                    JsonNode payload, Instant createdAt) {
        UiOutbox outbox = new UiOutbox();
        outbox.recipientUserId = DomainChecks.text(recipientUserId, "recipientUserId", 255);
        outbox.eventName = DomainChecks.text(eventName, "eventName", 50);
        outbox.payload = DomainChecks.required(payload, "payload").deepCopy();
        outbox.createdAt = DomainChecks.required(createdAt, "createdAt");
        return outbox;
    }

    public void markPublished(Instant now) {
        DomainChecks.required(now, "now");
        if (publishedAt == null) {
            publishedAt = now;
        }
    }

    public Long getOutboxId() { return outboxId; }
    public String getHouseholdId() { return householdId; }
    public String getRecipientUserId() { return recipientUserId; }
    public String getEventName() { return eventName; }
    public JsonNode getPayload() { return payload.deepCopy(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
