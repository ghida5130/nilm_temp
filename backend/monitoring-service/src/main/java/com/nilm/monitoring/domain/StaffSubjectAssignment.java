package com.nilm.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "staff_subject_assignment")
public class StaffSubjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "assigned_by_staff_id", nullable = false)
    private Long assignedByStaffId;

    @Column(columnDefinition = "text")
    private String memo;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    @Version
    @Column(nullable = false)
    private Long revision;

    protected StaffSubjectAssignment() {
    }

    private StaffSubjectAssignment(Long staffId, Long subjectId, Long assignedByStaffId,
                                   String memo, Instant now) {
        this.staffId = positiveId(staffId, "staffId");
        this.subjectId = positiveId(subjectId, "subjectId");
        this.assignedByStaffId = positiveId(assignedByStaffId, "assignedByStaffId");
        this.memo = memo;
        this.assignedAt = DomainChecks.required(now, "now");
        this.updatedAt = now;
    }

    public static StaffSubjectAssignment assign(Long staffId, Long subjectId,
                                                Long assignedByStaffId, String memo,
                                                Instant now) {
        return new StaffSubjectAssignment(staffId, subjectId, assignedByStaffId, memo, now);
    }

    public void updateMemo(String memo, Instant now) {
        requireActive();
        touch(now);
        this.memo = memo;
    }

    public void unassign(Instant now) {
        requireActive();
        touch(now);
        unassignedAt = now;
    }

    public boolean isActive() {
        return unassignedAt == null;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("An ended assignment cannot be changed");
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

    public Long getId() { return id; }
    public Long getStaffId() { return staffId; }
    public Long getSubjectId() { return subjectId; }
    public Long getAssignedByStaffId() { return assignedByStaffId; }
    public String getMemo() { return memo; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getUnassignedAt() { return unassignedAt; }
    public Long getRevision() { return revision; }
}
