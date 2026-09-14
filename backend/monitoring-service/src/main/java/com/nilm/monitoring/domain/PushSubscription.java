package com.nilm.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "push_subscription")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long subscriptionId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String endpoint;

    @Column(nullable = false, columnDefinition = "text")
    private String p256dh;

    @Column(nullable = false, columnDefinition = "text")
    private String auth;

    @Column(name = "expires_at")
    private Instant expirationTime;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PushSubscription() {
    }

    public PushSubscription(String userId, String endpoint, String p256dh, String auth,
                            Instant expirationTime, Instant now) {
        this.userId = DomainChecks.text(userId, "userId", 255);
        this.endpoint = requiredText(endpoint, "endpoint");
        this.p256dh = requiredText(p256dh, "p256dh");
        this.auth = requiredText(auth, "auth");
        this.expirationTime = expirationTime;
        this.createdAt = DomainChecks.required(now, "now");
        this.updatedAt = now;
    }

    public void refresh(String userId, String p256dh, String auth, Instant expirationTime, Instant now) {
        String validatedUserId = DomainChecks.text(userId, "userId", 255);
        String validatedP256dh = requiredText(p256dh, "p256dh");
        String validatedAuth = requiredText(auth, "auth");
        DomainChecks.chronological(updatedAt, now, "now");
        this.userId = validatedUserId;
        this.p256dh = validatedP256dh;
        this.auth = validatedAuth;
        this.expirationTime = expirationTime;
        this.revokedAt = null;
        this.updatedAt = now;
    }

    public void revoke(Instant now) {
        DomainChecks.chronological(updatedAt, now, "now");
        this.revokedAt = now;
        this.updatedAt = now;
    }

    private static String requiredText(String value, String name) {
        DomainChecks.required(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public Long getSubscriptionId() { return subscriptionId; }
    public String getUserId() { return userId; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
    public Instant getExpirationTime() { return expirationTime; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
