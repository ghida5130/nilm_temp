package com.nilm.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "household_access")
public class HouseholdAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "household_id", nullable = false, length = 50)
    private String householdId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "access_role", length = 30)
    private String accessRole;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected HouseholdAccess() {
    }

    public HouseholdAccess(String householdId, String userId, Instant createdAt) {
        this(householdId, userId, null, createdAt);
    }

    public HouseholdAccess(String householdId, String userId, String accessRole, Instant createdAt) {
        this.householdId = DomainChecks.text(householdId, "householdId", 50);
        this.userId = DomainChecks.text(userId, "userId", 255);
        this.accessRole = accessRole == null ? null : DomainChecks.text(accessRole, "accessRole", 30);
        this.createdAt = DomainChecks.required(createdAt, "createdAt");
    }

    public Long getId() { return id; }
    public String getHouseholdId() { return householdId; }
    public String getUserId() { return userId; }
    public String getAccessRole() { return accessRole; }
    public Instant getCreatedAt() { return createdAt; }
}
