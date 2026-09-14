package com.nilm.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_action")
public class IncidentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long actionId;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "notification_delivery_id")
    private UUID notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private ActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;

    @Column(name = "actor_id", length = 255)
    private String actorId;

    @Column(name = "actor_staff_id")
    private Long actorStaffId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private IncidentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false, length = 20)
    private IncidentStatus nextStatus;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncidentAction() {
    }

    public IncidentAction(UUID incidentId, UUID notificationId, ActionType actionType,
                          ActorType actorType, String actorId, IncidentStatus previousStatus,
                          IncidentStatus nextStatus, Instant occurredAt) {
        this(incidentId, notificationId, actionType, actorType, actorId, null,
                previousStatus, nextStatus, null, occurredAt, occurredAt);
    }

    public IncidentAction(UUID incidentId, UUID notificationId, ActionType actionType,
                          ActorType actorType, String actorId, Long actorStaffId,
                          IncidentStatus previousStatus, IncidentStatus nextStatus,
                          String note, Instant occurredAt, Instant createdAt) {
        this.incidentId = DomainChecks.required(incidentId, "incidentId");
        this.notificationId = notificationId;
        this.actionType = DomainChecks.required(actionType, "actionType");
        this.actorType = DomainChecks.required(actorType, "actorType");
        validateActor(actorType, actorId, actorStaffId);
        this.actorId = actorId == null ? null : DomainChecks.text(actorId, "actorId", 255);
        this.actorStaffId = actorStaffId;
        this.previousStatus = previousStatus;
        this.nextStatus = DomainChecks.required(nextStatus, "nextStatus");
        this.note = note;
        this.occurredAt = DomainChecks.required(occurredAt, "occurredAt");
        this.createdAt = DomainChecks.required(createdAt, "createdAt");
        if (createdAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("createdAt must not be before occurredAt");
        }
    }

    private static void validateActor(ActorType actorType, String actorId, Long actorStaffId) {
        switch (actorType) {
            case STAFF -> {
                if (actorStaffId == null || actorStaffId <= 0 || actorId != null) {
                    throw new IllegalArgumentException("STAFF actions require actorStaffId and no actorId");
                }
            }
            case USER -> {
                if (actorId == null || actorId.isBlank() || actorStaffId != null) {
                    throw new IllegalArgumentException("USER actions require actorId and no actorStaffId");
                }
            }
            case SYSTEM -> {
                if (actorId != null || actorStaffId != null) {
                    throw new IllegalArgumentException("SYSTEM actions must not have an actor id");
                }
            }
        }
    }

    public Long getActionId() { return actionId; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getNotificationId() { return notificationId; }
    public ActionType getActionType() { return actionType; }
    public ActorType getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public Long getActorStaffId() { return actorStaffId; }
    public IncidentStatus getPreviousStatus() { return previousStatus; }
    public IncidentStatus getNextStatus() { return nextStatus; }
    public String getNote() { return note; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
}
