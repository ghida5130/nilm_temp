package com.nilm.device.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 서비스 프로필 — Keycloak 사용자 1명당 1행.
 * 비밀번호·세션은 Keycloak 소관이며 여기에는 저장하지 않는다.
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    public enum Status {
        /** 기관 소속(복지사) 가입 — 관리자 승인 전 */
        PENDING,
        ACTIVE,
        SUSPENDED
    }

    @Id
    @Column(name = "keycloak_user_id")
    private UUID keycloakUserId;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(length = 20)
    private String phone;

    /** 기관 소속이면 값이 있고, 그 경우 가입 직후 상태는 PENDING */
    @Column(length = 100)
    private String organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected UserProfile() {
    }

    public UserProfile(UUID keycloakUserId, String email, String displayName,
                       String phone, String organization) {
        this.keycloakUserId = keycloakUserId;
        this.email = email;
        this.displayName = displayName;
        this.phone = phone;
        this.organization = organization;
        this.status = (organization == null || organization.isBlank()) ? Status.ACTIVE : Status.PENDING;
    }

    public void approve() {
        this.status = Status.ACTIVE;
    }

    public void suspend() {
        this.status = Status.SUSPENDED;
    }

    public UUID getKeycloakUserId() {
        return keycloakUserId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhone() {
        return phone;
    }

    public String getOrganization() {
        return organization;
    }

    public Status getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
