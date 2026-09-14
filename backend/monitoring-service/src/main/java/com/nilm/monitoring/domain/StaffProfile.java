package com.nilm.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "staff_profile")
public class StaffProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_sub", nullable = false, unique = true, length = 255)
    private String authSub;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "organization_name", nullable = false, length = 100)
    private String organizationName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long revision;

    protected StaffProfile() {
    }

    private StaffProfile(String authSub, String displayName, String organizationName, Instant now) {
        this.authSub = DomainChecks.text(authSub, "authSub", 255);
        this.displayName = DomainChecks.text(displayName, "displayName", 100);
        this.organizationName = DomainChecks.text(organizationName, "organizationName", 100);
        this.status = StaffStatus.ACTIVE;
        this.createdAt = DomainChecks.required(now, "now");
        this.updatedAt = now;
    }

    public static StaffProfile register(String authSub, String displayName,
                                        String organizationName, Instant now) {
        return new StaffProfile(authSub, displayName, organizationName, now);
    }

    public void updateProfile(String displayName, String organizationName, Instant now) {
        String validatedDisplayName = DomainChecks.text(displayName, "displayName", 100);
        String validatedOrganizationName = DomainChecks.text(organizationName, "organizationName", 100);
        updateTime(now);
        this.displayName = validatedDisplayName;
        this.organizationName = validatedOrganizationName;
    }

    public void activate(Instant now) {
        updateTime(now);
        status = StaffStatus.ACTIVE;
    }

    public void deactivate(Instant now) {
        updateTime(now);
        status = StaffStatus.INACTIVE;
    }

    public boolean isActive() {
        return status == StaffStatus.ACTIVE;
    }

    private void updateTime(Instant now) {
        DomainChecks.chronological(updatedAt, now, "now");
        updatedAt = now;
    }

    public Long getId() { return id; }
    public String getAuthSub() { return authSub; }
    public String getDisplayName() { return displayName; }
    public String getOrganizationName() { return organizationName; }
    public StaffStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getRevision() { return revision; }
}
