package com.nilm.monitoring.domain;

import com.nilm.monitoring.config.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Getter
@Table(name = "subjects")
public class Subject {

    /** 요청이 서버에 닿기까지의 시계 차이를 허용하는 폭. */
    private static final Duration START_TOLERANCE = Duration.ofMinutes(1);

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

    @Column(name = "state_version", nullable = false)
    private long stateVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_risk_level", nullable = false)
    private RiskLevel currentRiskLevel = RiskLevel.NORMAL;

    @Column(name = "current_risk_score", nullable = false)
    private int currentRiskScore;

    @Column(name = "last_activity_at")
    private OffsetDateTime lastActivityAt;

    @Column(name = "last_activity_appliance")
    private String lastActivityAppliance;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

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
        this.stateVersion = 1;
        this.currentRiskLevel = RiskLevel.NORMAL;
        this.currentRiskScore = 0;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

        // authSub에는 값을 넣지 않는다.
        // 대상자 본인의 로그인 계정 연결 시 별도로 설정한다.
    }

    public void assignManager(Long managerId) {
        this.managerId = managerId;
    }

    public void applyMonitoringEvent(
            RiskLevel riskLevel,
            int riskScore,
            String applianceType,
            OffsetDateTime occurredAt,
            OffsetDateTime updatedAt
    ) {
        this.currentRiskLevel = riskLevel;
        this.currentRiskScore = riskScore;
        if (applianceType != null && !applianceType.isBlank()
                && (lastActivityAt == null || !occurredAt.isBefore(lastActivityAt))) {
            this.lastActivityAt = occurredAt;
            this.lastActivityAppliance = applianceType;
        }
        touch(updatedAt);
    }

    public void touch(OffsetDateTime updatedAt) {
        this.stateVersion++;
        this.updatedAt = updatedAt;
    }

    /**
     * 주어진 시각이 외출 구간 안인지 판정한다.
     *
     * <p>{@code awayUntil}이 없으면 해제할 때까지 이어지는 무기한 외출이다.
     * 시각으로 판정하므로 이벤트가 늦게 도착해도 같은 답이 나온다.
     */
    public boolean isAwayAt(OffsetDateTime at) {
        if (awayStartedAt == null || at.isBefore(awayStartedAt)) {
            return false;
        }
        return awayUntil == null || at.isBefore(awayUntil);
    }

    /** 아직 시작하지 않은 외출 예약이 걸려 있는지. */
    public boolean hasScheduledAway(OffsetDateTime at) {
        return awayStartedAt != null && at.isBefore(awayStartedAt);
    }

    /**
     * 외출을 설정한다. {@code startsAt}이 없으면 즉시 시작하고,
     * {@code endsAt}이 없으면 해제할 때까지 이어진다.
     *
     * <p>구간은 한 벌만 보관하므로 새로 설정하면 이전 외출 기록을 덮어쓴다.
     */
    public void scheduleAway(
            OffsetDateTime now,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {
        if (startsAt != null && startsAt.isBefore(now.minus(START_TOLERANCE))) {
            throw new IllegalArgumentException(
                    "외출 시작 시간은 현재 시각 이후여야 합니다."
            );
        }

        OffsetDateTime start = startsAt == null ? now : startsAt;
        if (endsAt != null && !endsAt.isAfter(start)) {
            throw new IllegalArgumentException(
                    "외출 종료 시간은 시작 시간 이후여야 합니다."
            );
        }

        this.awayStartedAt = start;
        this.awayUntil = endsAt;
        syncMonitoring(now);
    }

    /**
     * 외출을 해제한다. 진행 중이면 지금 끊되 구간은 남겨 둔다.
     * 늦게 도착한 이벤트가 외출 중 발생이었는지 판정할 수 있어야 하기 때문이다.
     */
    public void cancelAway(OffsetDateTime now) {
        if (awayStartedAt == null) {
            return;
        }

        if (isAwayAt(now)) {
            this.awayUntil = now;
        } else if (now.isBefore(awayStartedAt)) {
            // 아직 시작하지 않은 예약은 흔적 없이 취소한다.
            this.awayStartedAt = null;
            this.awayUntil = null;
        }
        syncMonitoring(now);
    }

    /**
     * 외출 구간에서 계산한 값으로 모니터링 플래그를 맞춘다.
     *
     * <p>이 플래그는 목록 조회와 실시간 전송을 위한 캐시일 뿐이고,
     * 판단의 근거는 언제나 {@link #isAwayAt(OffsetDateTime)}이다.
     *
     * @return 값이 실제로 바뀌었으면 true
     */
    public boolean syncMonitoring(OffsetDateTime now) {
        boolean next = !isAwayAt(now);
        if (next == monitoringEnabled) {
            return false;
        }

        this.monitoringEnabled = next;
        touch(now);
        return true;
    }
}
