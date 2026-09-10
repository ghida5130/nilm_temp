package com.nilm.monitoring.notification;

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

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, unique = true)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
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
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.expirationTime = expirationTime;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void refresh(String userId, String p256dh, String auth, Instant expirationTime, Instant now) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.expirationTime = expirationTime;
        this.revokedAt = null;
        this.updatedAt = now;
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
        this.updatedAt = now;
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
