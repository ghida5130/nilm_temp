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

@Entity
@Table(name = "notification_preference")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false)
    private Boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "minimum_severity", nullable = false, length = 20)
    private Severity minimumSeverity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
    }

    public NotificationPreference(Long staffId, String notificationType,
                                  NotificationChannel channel, boolean enabled,
                                  Severity minimumSeverity, Instant updatedAt) {
        this.staffId = positiveId(staffId);
        this.notificationType = DomainChecks.text(notificationType, "notificationType", 50);
        this.channel = DomainChecks.required(channel, "channel");
        this.enabled = enabled;
        this.minimumSeverity = DomainChecks.required(minimumSeverity, "minimumSeverity");
        this.updatedAt = DomainChecks.required(updatedAt, "updatedAt");
    }

    public void changePreference(boolean enabled, Severity minimumSeverity, Instant now) {
        Severity validatedSeverity = DomainChecks.required(minimumSeverity, "minimumSeverity");
        DomainChecks.chronological(updatedAt, now, "now");
        this.enabled = enabled;
        this.minimumSeverity = validatedSeverity;
        this.updatedAt = now;
    }

    public boolean accepts(Severity severity) {
        return Boolean.TRUE.equals(enabled) && minimumSeverity.includes(severity);
    }

    private static Long positiveId(Long value) {
        DomainChecks.required(value, "staffId");
        if (value <= 0) {
            throw new IllegalArgumentException("staffId must be positive");
        }
        return value;
    }

    public Long getId() { return id; }
    public Long getStaffId() { return staffId; }
    public String getNotificationType() { return notificationType; }
    public NotificationChannel getChannel() { return channel; }
    public Boolean getEnabled() { return enabled; }
    public Severity getMinimumSeverity() { return minimumSeverity; }
    public Instant getUpdatedAt() { return updatedAt; }
}
