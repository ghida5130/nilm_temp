package com.nilm.monitoring.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 대상자 상세 화면의 하루 전력 사용 패턴 그래프 응답.
 * 구간은 {@code timezone} 기준 0시부터 23시까지 항상 24개를 채워서 내려보낸다.
 */
public record SubjectPowerUsageResponse(
        String subjectId,
        LocalDate date,
        String timezone,
        int intervalMinutes,
        String unit,
        BigDecimal totalUsage,
        List<HourlyUsage> hourlyUsage,
        List<ApplianceUsage> appliances,
        OffsetDateTime updatedAt
) {

    /** 구간별 집계 상태. */
    public enum BucketStatus {
        COMPLETE, // 구간이 끝나 집계가 확정됨
        PARTIAL,  // 구간이 진행 중이라 값이 더 늘어날 수 있음
        NOT_YET   // 아직 도달하지 않은 시간
    }

    public record HourlyUsage(
            int hour,
            BigDecimal usage,
            BucketStatus status
    ) {
    }

    public record ApplianceUsage(
            String applianceType,
            BigDecimal totalUsage,
            List<HourlyUsage> hourlyUsage
    ) {
    }
}
