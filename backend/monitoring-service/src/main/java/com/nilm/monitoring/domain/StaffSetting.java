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
@Table(name = "staff_setting")
public class StaffSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false, unique = true)
    private Long staffId;

    @Column(name = "danger_enabled", nullable = false)
    private boolean dangerEnabled = true;

    @Column(name = "warning_enabled", nullable = false)
    private boolean warningEnabled = true;

    @Column(name = "daily_summary_enabled", nullable = false)
    private boolean dailySummaryEnabled;

    @Column(name = "sound_enabled", nullable = false)
    private boolean soundEnabled;

    @Column(name = "default_policy_id")
    private Long defaultPolicyId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long revision;

    protected StaffSetting() {
    }

    public StaffSetting(Long staffId, Instant now) {
        if (staffId == null || staffId <= 0) throw new IllegalArgumentException("staffId must be positive");
        this.staffId = staffId;
        this.updatedAt = DomainChecks.required(now, "now");
    }

    public void updateNotifications(boolean dangerEnabled, boolean warningEnabled,
                                    boolean dailySummaryEnabled, boolean soundEnabled, Instant now) {
        this.dangerEnabled = dangerEnabled;
        this.warningEnabled = warningEnabled;
        this.dailySummaryEnabled = dailySummaryEnabled;
        this.soundEnabled = soundEnabled;
        touch(now);
    }

    public void applyDefaultPolicy(Long policyId, Instant now) {
        if (policyId == null || policyId <= 0) throw new IllegalArgumentException("policyId must be positive");
        this.defaultPolicyId = policyId;
        touch(now);
    }

    private void touch(Instant now) {
        DomainChecks.chronological(updatedAt, now, "now");
        updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getStaffId() { return staffId; }
    public boolean isDangerEnabled() { return dangerEnabled; }
    public boolean isWarningEnabled() { return warningEnabled; }
    public boolean isDailySummaryEnabled() { return dailySummaryEnabled; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public Long getDefaultPolicyId() { return defaultPolicyId; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getRevision() { return revision; }
}
