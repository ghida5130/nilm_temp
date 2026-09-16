package com.nilm.monitoring.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Getter
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "household_id", nullable = false)
    private String householdId;

    @Column(name = "auth_sub", unique = true)
    private String authSub;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column(name = "risk_policy_id")
    private Long riskPolicyId;

    @Column(name = "monitoring_enabled", nullable = false)
    private boolean monitoringEnabled = true;

    @Column(name = "away_started_at")
    private OffsetDateTime awayStartedAt;

    @Column(name = "away_until")
    private OffsetDateTime awayUntil;

    @Column(name = "manager_memo", columnDefinition = "text")
    private String managerMemo;

    @Column(name = "manager_id")
    private Long managerId;

    protected Subject() {
    }

    public void assignManager(Long managerId) {
        this.managerId = managerId;
    }

    public void startAway(
            OffsetDateTime now,
            OffsetDateTime until
    ) {
        if (until == null || !until.isAfter(now)) {
            throw new IllegalArgumentException(
                    "외출 종료 시간은 시작 시간 이후여야 합니다."
            );
        }
        if (!monitoringEnabled) {
            throw new IllegalStateException(
                    "이미 모니터링이 중지되어 있습니다."
            );
        }

        this.awayStartedAt = now;
        this.awayUntil = until;
        this.monitoringEnabled = false;
    }

    public void endAway(OffsetDateTime now) {
        if (monitoringEnabled || awayStartedAt == null
                || awayUntil == null) {
            return;
        }

        if (now.isBefore(awayStartedAt)) {
            throw new IllegalArgumentException(
                    "외출 종료 시간은 시작 시간보다 빠를 수 없습니다."
            );
        }

        // 예정 시간 이후에 처리되더라도 원래 종료 시간을 유지
        if (now.isBefore(awayUntil)) {
            this.awayUntil = now;
        }
        this.monitoringEnabled = true;
    }
}
