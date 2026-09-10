package com.nilm.monitoring.incident;

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

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected HouseholdAccess() {
    }

    public HouseholdAccess(String householdId, String userId, Instant createdAt) {
        this.householdId = householdId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getHouseholdId() { return householdId; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
}
