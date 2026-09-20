package com.nilm.monitoring.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;

/**
 * 가구·가전·영업일(KST)별 당일 사용 사실.
 *
 * <p>루틴 미사용(M)은 "오늘 이미 썼는가"를, 활동 감소(A)는 "오늘 몇 번 켰는가"를
 * 여기서 읽는다. 프로필의 일별 집계와 같은 영업일 경계를 쓴다.
 */
@Entity
@Getter
@Table(name = "household_daily_appliance_usage")
@IdClass(HouseholdDailyApplianceUsageId.class)
public class HouseholdDailyApplianceUsage {

    @Id
    @Column(name = "household_id", nullable = false)
    private String householdId;

    @Id
    @Column(name = "appliance_type", nullable = false, length = 50)
    private String applianceType;

    @Id
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "first_on_at", nullable = false)
    private OffsetDateTime firstOnAt;

    @Column(name = "last_on_at", nullable = false)
    private OffsetDateTime lastOnAt;

    @Column(name = "start_count", nullable = false)
    private int startCount;

    protected HouseholdDailyApplianceUsage() {
    }

    public HouseholdDailyApplianceUsage(
            String householdId,
            String applianceType,
            LocalDate usageDate,
            OffsetDateTime firstOnAt
    ) {
        this.householdId = householdId;
        this.applianceType = applianceType;
        this.usageDate = usageDate;
        this.firstOnAt = firstOnAt;
        this.lastOnAt = firstOnAt;
        this.startCount = 1;
    }

    /**
     * 같은 날 다시 켜졌다.
     * 첫 사용 시각은 한 번만 채우고 이후 전환으로 덮어쓰지 않는다.
     */
    public void recordStart(OffsetDateTime startedAt) {
        if (startedAt.isBefore(firstOnAt)) {
            // 늦게 도착한 과거 전환이라면 첫 사용 시각만 앞당긴다.
            this.firstOnAt = startedAt;
        }
        if (startedAt.isAfter(lastOnAt)) {
            this.lastOnAt = startedAt;
        }
        this.startCount++;
    }
}
