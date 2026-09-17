package com.nilm.monitoring.service;

import com.nilm.monitoring.domain.HourlyAppliancePowerUsage;
import com.nilm.monitoring.domain.HourlyPowerUsage;
import com.nilm.monitoring.domain.Subject;
import com.nilm.monitoring.dto.SubjectPowerUsageResponse;
import com.nilm.monitoring.dto.SubjectPowerUsageResponse.BucketStatus;
import com.nilm.monitoring.dto.SubjectPowerUsageResponse.HourlyUsage;
import com.nilm.monitoring.repository.HourlyAppliancePowerUsageRepository;
import com.nilm.monitoring.repository.HourlyPowerUsageRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대상자 상세 화면의 하루 전력 사용 패턴 그래프를 만든다.
 * 저장은 UTC 정각 구간이고, 응답은 {@link #ZONE} 기준 0~23시 24개 구간으로 고정한다.
 */
@Service
@RequiredArgsConstructor
public class SubjectPowerUsageService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int BUCKET_COUNT = 24;
    private static final int INTERVAL_MINUTES = 60;
    private static final String UNIT = "Wh";
    private static final int USAGE_SCALE = 1;

    private final SubjectAccessGuard accessGuard;
    private final HourlyPowerUsageRepository totals;
    private final HourlyAppliancePowerUsageRepository appliances;

    @Transactional(readOnly = true)
    public SubjectPowerUsageResponse getPowerUsage(
            String authSub,
            Long subjectId,
            LocalDate requestedDate
    ) {
        Subject subject = accessGuard.requireReadable(authSub, subjectId);

        LocalDate date = requestedDate == null ? LocalDate.now(ZONE) : requestedDate;
        ZonedDateTime dayStart = date.atStartOfDay(ZONE);
        OffsetDateTime from = dayStart.toOffsetDateTime();
        OffsetDateTime until = dayStart.plusDays(1).toOffsetDateTime();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<HourlyPowerUsage> totalRows = totals
                .findAllByHouseholdIdAndBucketStartAtGreaterThanEqualAndBucketStartAtLessThanOrderByBucketStartAtAsc(
                        subject.getHouseholdId(), from, until);
        List<HourlyAppliancePowerUsage> applianceRows = appliances
                .findAllByHouseholdIdAndBucketStartAtGreaterThanEqualAndBucketStartAtLessThanOrderByApplianceTypeAscBucketStartAtAsc(
                        subject.getHouseholdId(), from, until);

        BigDecimal[] totalBuckets = emptyBuckets();
        for (HourlyPowerUsage row : totalRows) {
            add(totalBuckets, dayStart, row.getBucketStartAt(), row.getEnergyWh());
        }

        // 가전 코드 순으로 고정해 프론트가 매번 같은 순서의 계열을 그린다.
        Map<String, BigDecimal[]> byAppliance = new TreeMap<>();
        for (HourlyAppliancePowerUsage row : applianceRows) {
            add(
                    byAppliance.computeIfAbsent(row.getApplianceType(), key -> emptyBuckets()),
                    dayStart,
                    row.getBucketStartAt(),
                    row.getEnergyWh()
            );
        }

        List<HourlyUsage> hourlyUsage = toHourlyUsage(totalBuckets, dayStart, now);
        List<SubjectPowerUsageResponse.ApplianceUsage> applianceUsages = byAppliance.entrySet().stream()
                .map(entry -> {
                    List<HourlyUsage> buckets = toHourlyUsage(entry.getValue(), dayStart, now);
                    return new SubjectPowerUsageResponse.ApplianceUsage(
                            entry.getKey(), sum(buckets), buckets);
                })
                .toList();

        return new SubjectPowerUsageResponse(
                subject.getId().toString(),
                date,
                ZONE.getId(),
                INTERVAL_MINUTES,
                UNIT,
                sum(hourlyUsage),
                hourlyUsage,
                applianceUsages,
                latestUpdatedAt(totalRows, applianceRows)
        );
    }

    private BigDecimal[] emptyBuckets() {
        BigDecimal[] buckets = new BigDecimal[BUCKET_COUNT];
        java.util.Arrays.fill(buckets, BigDecimal.ZERO);
        return buckets;
    }

    /**
     * 저장 구간 시작 시각을 화면 기준 시(0~23)로 옮겨 담는다.
     * 조회 범위 밖의 값은 들어올 수 없지만, 방어적으로 무시한다.
     */
    private void add(
            BigDecimal[] buckets,
            ZonedDateTime dayStart,
            OffsetDateTime bucketStartAt,
            BigDecimal energyWh
    ) {
        int hour = (int) java.time.Duration
                .between(dayStart.toInstant(), bucketStartAt.toInstant())
                .toHours();
        if (hour < 0 || hour >= BUCKET_COUNT) {
            return;
        }
        buckets[hour] = buckets[hour].add(energyWh == null ? BigDecimal.ZERO : energyWh);
    }

    private List<HourlyUsage> toHourlyUsage(
            BigDecimal[] buckets,
            ZonedDateTime dayStart,
            OffsetDateTime now
    ) {
        List<HourlyUsage> result = new ArrayList<>(BUCKET_COUNT);
        for (int hour = 0; hour < BUCKET_COUNT; hour++) {
            OffsetDateTime bucketStart = dayStart.plusHours(hour).toOffsetDateTime();
            OffsetDateTime bucketEnd = dayStart.plusHours(hour + 1L).toOffsetDateTime();
            BucketStatus status = statusOf(bucketStart, bucketEnd, now);
            BigDecimal usage = status == BucketStatus.NOT_YET
                    ? null
                    : buckets[hour].setScale(USAGE_SCALE, RoundingMode.HALF_UP);
            result.add(new HourlyUsage(hour, usage, status));
        }
        return result;
    }

    private BucketStatus statusOf(
            OffsetDateTime bucketStart,
            OffsetDateTime bucketEnd,
            OffsetDateTime now
    ) {
        if (!now.isAfter(bucketStart)) {
            return BucketStatus.NOT_YET;
        }
        if (now.isBefore(bucketEnd)) {
            return BucketStatus.PARTIAL;
        }
        return BucketStatus.COMPLETE;
    }

    private BigDecimal sum(List<HourlyUsage> buckets) {
        BigDecimal total = BigDecimal.ZERO;
        for (HourlyUsage bucket : buckets) {
            if (bucket.usage() != null) {
                total = total.add(bucket.usage());
            }
        }
        return total.setScale(USAGE_SCALE, RoundingMode.HALF_UP);
    }

    /** 그래프가 어느 시점까지의 집계인지 알려주는 값. 데이터가 없으면 null. */
    private OffsetDateTime latestUpdatedAt(
            List<HourlyPowerUsage> totalRows,
            List<HourlyAppliancePowerUsage> applianceRows
    ) {
        OffsetDateTime latest = null;
        for (HourlyPowerUsage row : totalRows) {
            latest = later(latest, row.getUpdatedAt());
        }
        for (HourlyAppliancePowerUsage row : applianceRows) {
            latest = later(latest, row.getUpdatedAt());
        }
        return latest;
    }

    private OffsetDateTime later(OffsetDateTime current, OffsetDateTime candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }
}
