package com.nilm.device.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 가구 ↔ 보호자(Keycloak 사용자) 매핑.
 * 사용자 신원(이름·이메일·비밀번호)은 Keycloak 소관 — 여기에는
 * "우리 서비스에서의 관계"(가구, 역할, 알림 연락처)만 둔다.
 */
@Entity
@Table(name = "household_guardians")
@IdClass(HouseholdGuardian.GuardianId.class)
public class HouseholdGuardian {

    public enum Role {
        PRIMARY, SECONDARY
    }

    @Id
    @Column(name = "house_id", length = 10)
    private String houseId;

    @Id
    @Column(name = "keycloak_user_id")
    private UUID keycloakUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.PRIMARY;

    @Column(name = "notify_phone", nullable = false, length = 20)
    private String notifyPhone;

    @Column(name = "notify_enabled", nullable = false)
    private boolean notifyEnabled = true;

    protected HouseholdGuardian() {
    }

    public HouseholdGuardian(String houseId, UUID keycloakUserId, Role role,
                             String notifyPhone, Boolean notifyEnabled) {
        this.houseId = houseId;
        this.keycloakUserId = keycloakUserId;
        if (role != null) {
            this.role = role;
        }
        this.notifyPhone = notifyPhone;
        if (notifyEnabled != null) {
            this.notifyEnabled = notifyEnabled;
        }
    }

    /** 부분 수정 — null인 필드는 유지 */
    public void update(Role role, String notifyPhone, Boolean notifyEnabled) {
        if (role != null) {
            this.role = role;
        }
        if (notifyPhone != null) {
            this.notifyPhone = notifyPhone;
        }
        if (notifyEnabled != null) {
            this.notifyEnabled = notifyEnabled;
        }
    }

    public String getHouseId() {
        return houseId;
    }

    public UUID getKeycloakUserId() {
        return keycloakUserId;
    }

    public Role getRole() {
        return role;
    }

    public String getNotifyPhone() {
        return notifyPhone;
    }

    public boolean isNotifyEnabled() {
        return notifyEnabled;
    }

    /** 복합 키 (house_id + keycloak_user_id) */
    public static class GuardianId implements Serializable {

        private String houseId;
        private UUID keycloakUserId;

        public GuardianId() {
        }

        public GuardianId(String houseId, UUID keycloakUserId) {
            this.houseId = houseId;
            this.keycloakUserId = keycloakUserId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof GuardianId other
                    && Objects.equals(houseId, other.houseId)
                    && Objects.equals(keycloakUserId, other.keycloakUserId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(houseId, keycloakUserId);
        }
    }
}
