package com.nilm.monitoring.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_delivery")
public class NotificationDelivery {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "recipient_user_id", nullable = false, length = 255)
    private String recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 10)
    private DeliveryStatus deliveryStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "response_deadline_at")
    private Instant responseDeadlineAt;

    @Column(length = 10)
    private String answer;

    @Column(name = "response_source", length = 10)
    private String responseSource;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationDelivery() {
    }

    public NotificationDelivery(UUID id, UUID incidentId, String recipientUserId,
                                JsonNode payload, Instant createdAt) {
        this(id, incidentId, recipientUserId, NotificationChannel.WEB_PUSH, payload, createdAt);
    }

    public NotificationDelivery(UUID id, UUID incidentId, String recipientUserId,
                                NotificationChannel channel, JsonNode payload, Instant createdAt) {
        this.id = DomainChecks.required(id, "id");
        this.incidentId = DomainChecks.required(incidentId, "incidentId");
        this.recipientUserId = DomainChecks.text(recipientUserId, "recipientUserId", 255);
        this.channel = DomainChecks.required(channel, "channel");
        this.deliveryStatus = DeliveryStatus.PENDING;
        this.payload = DomainChecks.required(payload, "payload").deepCopy();
        this.createdAt = DomainChecks.required(createdAt, "createdAt");
    }

    public void markSent(Instant sentAt, Duration responseTimeout, String partialFailureReason) {
        DomainChecks.required(sentAt, "sentAt");
        DomainChecks.required(responseTimeout, "responseTimeout");
        if (responseTimeout.isNegative() || responseTimeout.isZero()) {
            throw new IllegalArgumentException("responseTimeout must be positive");
        }
        deliveryStatus = DeliveryStatus.SENT;
        this.sentAt = sentAt;
        responseDeadlineAt = sentAt.plus(responseTimeout);
        failureReason = partialFailureReason;
    }

    public void markFailed(String reason) {
        DomainChecks.required(reason, "reason");
        deliveryStatus = DeliveryStatus.FAILED;
        failureReason = reason;
        sentAt = null;
        responseDeadlineAt = null;
    }

    public void respond(String answer, String source, Instant now) {
        if (this.answer != null) {
            throw new IllegalStateException("Notification delivery already has a response");
        }
        if (!"yes".equals(answer) && !"no".equals(answer)) {
            throw new IllegalArgumentException("answer must be yes or no");
        }
        if (!"user".equals(source) && !"timeout".equals(source)) {
            throw new IllegalArgumentException("source must be user or timeout");
        }
        DomainChecks.required(now, "now");
        this.answer = answer;
        responseSource = source;
        respondedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public String getRecipientUserId() { return recipientUserId; }
    public NotificationChannel getChannel() { return channel; }
    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public JsonNode getPayload() { return payload.deepCopy(); }
    public Instant getSentAt() { return sentAt; }
    public Instant getResponseDeadlineAt() { return responseDeadlineAt; }
    public String getAnswer() { return answer; }
    public String getResponseSource() { return responseSource; }
    public Instant getRespondedAt() { return respondedAt; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
