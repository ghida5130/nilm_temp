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
    @Column(name = "actor_type", nullable = false, length = 10)
    private ActorType actorType;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private IncidentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false, length = 20)
    private IncidentStatus nextStatus;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected IncidentAction() {
    }

    public IncidentAction(UUID incidentId, UUID notificationId, ActionType actionType,
                          ActorType actorType, String actorId, IncidentStatus previousStatus,
                          IncidentStatus nextStatus, Instant occurredAt) {
        this.incidentId = incidentId;
        this.notificationId = notificationId;
        this.actionType = actionType;
        this.actorType = actorType;
        this.actorId = actorId;
        this.previousStatus = previousStatus;
        this.nextStatus = nextStatus;
        this.occurredAt = occurredAt;
    }

    public Long getActionId() { return actionId; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getNotificationId() { return notificationId; }
    public ActionType getActionType() { return actionType; }
    public ActorType getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public IncidentStatus getPreviousStatus() { return previousStatus; }
    public IncidentStatus getNextStatus() { return nextStatus; }
    public Instant getOccurredAt() { return occurredAt; }
}
