package com.nilm.monitoring.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @Column(name = "id", nullable = false)
    private UUID incidentId;

    @Column(name = "analysis_event_id", nullable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "household_id", nullable = false, length = 50)
    private String householdId;

    @Column(name = "incident_type", nullable = false, length = 40)
    private String incidentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Incident() {
    }

    public Incident(UUID incidentId, UUID sourceEventId, String householdId,
                    String incidentType, Instant openedAt) {
        this.incidentId = incidentId;
        this.sourceEventId = sourceEventId;
        this.householdId = householdId;
        this.incidentType = incidentType;
        this.status = IncidentStatus.OPEN;
        this.openedAt = openedAt;
        this.updatedAt = openedAt;
    }

    public void close(IncidentStatus nextStatus, Instant occurredAt) {
        if (status != IncidentStatus.OPEN) {
            throw new IllegalStateException("Only an open incident can be closed");
        }
        status = nextStatus;
        closedAt = occurredAt;
        updatedAt = occurredAt;
    }

    public UUID getIncidentId() { return incidentId; }
    public UUID getSourceEventId() { return sourceEventId; }
    public String getHouseholdId() { return householdId; }
    public String getIncidentType() { return incidentType; }
    public IncidentStatus getStatus() { return status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
