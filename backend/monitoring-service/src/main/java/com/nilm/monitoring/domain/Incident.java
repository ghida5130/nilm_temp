package com.nilm.monitoring.domain;

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

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "household_id", nullable = false, length = 50)
    private String householdId;

    @Column(name = "incident_type", nullable = false, length = 50)
    private String incidentType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "assigned_staff_id")
    private Long assignedStaffId;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "cleared_event_id")
    private UUID clearedEventId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_code", length = 30)
    private String resolutionCode;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Incident() {
    }

    public Incident(UUID incidentId, UUID sourceEventId, String householdId,
                    String incidentType, Instant openedAt)
    {
        this(incidentId, sourceEventId, null, householdId, incidentType, null, openedAt);
    }

    private Incident(UUID incidentId, UUID sourceEventId, Long subjectId, String householdId,
                     String incidentType, Severity severity, Instant openedAt) {
        this.incidentId = DomainChecks.required(incidentId, "incidentId");
        this.sourceEventId = DomainChecks.required(sourceEventId, "sourceEventId");
        this.subjectId = positiveIdOrNull(subjectId, "subjectId");
        this.householdId = DomainChecks.text(householdId, "householdId", 50);
        this.incidentType = DomainChecks.text(incidentType, "incidentType", 50);
        this.severity = severity;
        this.status = IncidentStatus.OPEN;
        this.openedAt = DomainChecks.required(openedAt, "openedAt");
        this.updatedAt = openedAt;
    }

    public static Incident open(UUID incidentId, UUID sourceEventId, Long subjectId,
                                String householdId, String incidentType, Severity severity,
                                Instant openedAt) {
        return new Incident(incidentId, sourceEventId, subjectId, householdId,
                incidentType, severity, openedAt);
    }

    public void assign(Long staffId, Instant now) {
        requireOngoing();
        Long validatedStaffId = positiveId(staffId, "staffId");
        touch(now);
        assignedStaffId = validatedStaffId;
    }

    public void acknowledge(Instant now) {
        if (status == IncidentStatus.ACKNOWLEDGED) {
            return;
        }
        if (status != IncidentStatus.OPEN) {
            throw new IllegalStateException("Only an open incident can be acknowledged");
        }
        touch(now);
        status = IncidentStatus.ACKNOWLEDGED;
        acknowledgedAt = now;
    }

    public void acknowledge(Long staffId, Instant now) {
        if (status != IncidentStatus.OPEN && status != IncidentStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("Only an open incident can be acknowledged");
        }
        Long validatedStaffId = positiveId(staffId, "staffId");
        DomainChecks.chronological(updatedAt, now, "now");
        if (assignedStaffId == null) {
            assignedStaffId = validatedStaffId;
        } else if (!assignedStaffId.equals(validatedStaffId)) {
            throw new IllegalStateException("Only the assigned staff member can acknowledge the incident");
        }
        acknowledge(now);
    }

    public void recordClearance(UUID clearanceEventId, Instant now) {
        requireOngoing();
        DomainChecks.required(clearanceEventId, "clearanceEventId");
        if (clearedEventId != null) {
            if (clearedEventId.equals(clearanceEventId)) {
                return;
            }
            throw new IllegalStateException("A clearance event is already recorded");
        }
        touch(now);
        clearedEventId = clearanceEventId;
    }

    public void resolve(Long staffId, String resolutionCode, Instant now) {
        requireOngoing();
        Long validatedStaffId = positiveId(staffId, "staffId");
        String validatedResolutionCode = DomainChecks.text(resolutionCode, "resolutionCode", 30);
        if (assignedStaffId != null && !assignedStaffId.equals(validatedStaffId)) {
            throw new IllegalStateException("Only the assigned staff member can resolve the incident");
        }
        touch(now);
        assignedStaffId = validatedStaffId;
        status = IncidentStatus.RESOLVED;
        resolvedAt = now;
        this.resolutionCode = validatedResolutionCode;
    }

    public void resolve(String resolutionCode, Instant now) {
        if (assignedStaffId == null) {
            throw new IllegalStateException("An incident must be assigned before it can be resolved");
        }
        resolve(assignedStaffId, resolutionCode, now);
    }

    public void close(IncidentStatus nextStatus, Instant occurredAt) {
        if (status != IncidentStatus.OPEN) {
            throw new IllegalStateException("Only an open incident can be closed");
        }
        if (nextStatus != IncidentStatus.CONFIRMED
                && nextStatus != IncidentStatus.FALSE_POSITIVE) {
            throw new IllegalArgumentException("Legacy close only supports legacy response statuses");
        }
        touch(occurredAt);
        status = nextStatus;
        closedAt = occurredAt;
    }

    private void requireOngoing() {
        if (status != IncidentStatus.OPEN && status != IncidentStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("A closed incident cannot be changed");
        }
    }

    private void touch(Instant now) {
        DomainChecks.chronological(updatedAt, now, "now");
        updatedAt = now;
    }

    private static Long positiveId(Long value, String name) {
        DomainChecks.required(value, name);
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Long positiveIdOrNull(Long value, String name) {
        return value == null ? null : positiveId(value, name);
    }

    public UUID getIncidentId() { return incidentId; }
    public UUID getSourceEventId() { return sourceEventId; }
    public Long getSubjectId() { return subjectId; }
    public String getHouseholdId() { return householdId; }
    public String getIncidentType() { return incidentType; }
    public Severity getSeverity() { return severity; }
    public IncidentStatus getStatus() { return status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Long getAssignedStaffId() { return assignedStaffId; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public UUID getClearedEventId() { return clearedEventId; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolutionCode() { return resolutionCode; }
    public Instant getUpdatedAt() { return updatedAt; }
}
