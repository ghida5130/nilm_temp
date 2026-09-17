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
    private Long riskPolicyId; // 위험 정책 ID

    @Column(name = "monitoring_enabled", nullable = false)
    private boolean monitoringEnabled = true; // 모니터링 활성화 여부

    @Column(name = "away_started_at")
    private OffsetDateTime awayStartedAt; // 외출 시작 시간

    @Column(name = "away_until")
    private OffsetDateTime awayUntil; // 외출 종료 시간

    @Column(name = "manager_memo", columnDefinition = "text")
    private String managerMemo; // 담당자 메모

    @Column(name = "manager_id")
    private Long managerId; // 배정 담당자 ID, 대상자 1명 -> 담당자 1명만 배정

    @Column(name = "address_detail", length = 100)
    private String addressDetail;

    // 집주소 좌표, 성별, 사생활모드 여부, 위험 상태(위험,주의,정상) 추가
    // 마지막 활동 시간 ( On/Off 감지시 update )
    // 마지막 활동 가전 종류 ( 마지막 활동 시간 갱신시 )
    protected Subject() {
    }

    public Subject(
            String householdId,
            String name,
            LocalDate birthDate,
            String phone,
            String address,
            String addressDetail,
            String managerMemo,
            Long managerId
    ) {
        if (managerId == null) {
            throw new IllegalArgumentException(
                    "대상자를 등록할 담당자가 필요합니다."
            );
        }

        this.householdId = householdId;
        this.name = name;
        this.birthDate = birthDate;
        this.phone = phone;
        this.address = address;
        this.addressDetail = addressDetail;
        this.managerMemo = managerMemo;

        // 로그인한 담당자의 DB ID를 배정한다.
        this.managerId = managerId;

        this.monitoringEnabled = true;

        // authSub에는 값을 넣지 않는다.
        // 대상자 본인의 로그인 계정 연결 시 별도로 설정한다.
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
