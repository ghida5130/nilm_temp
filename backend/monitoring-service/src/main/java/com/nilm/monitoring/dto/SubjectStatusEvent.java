package com.nilm.monitoring.dto;

import com.nilm.monitoring.config.enums.RiskLevel;
import com.nilm.monitoring.config.enums.StateChangeTrigger;
import com.nilm.monitoring.domain.Notification;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 담당자 대시보드 실시간 스트림({@code GET /api/monitoring/stream})이
 * {@code subject-status} 이벤트로 내려보내는 대상자 1명의 현재 상태.
 * 목록 전체를 다시 보내지 않고 바뀐 대상자만 갱신한다.
 */
public record SubjectStatusEvent(
        String subjectId,
        long version,
        StateChangeTrigger trigger,
        RiskLevel riskLevel,
        int riskScore,
        long recentEventCount,
        LastActivity lastActivity,
        long unresolvedAlertCount,
        LatestAlert latestAlert,
        LastDetection lastDetection,
        OffsetDateTime updatedAt
) {

    /** 최근 가전 활동. 기록이 없으면 null. */
    public record LastActivity(
            OffsetDateTime occurredAt,
            String applianceType
    ) {
    }

    /** 가장 최근 생성된 위험 알림. 알림이 없으면 null. */
    public record LatestAlert(
            String alertId,
            String eventId,
            Notification.ManagerResponseStatus managerStatus,
            OffsetDateTime managerStatusUpdatedAt,
            SubjectResponse subjectResponse
    ) {
    }

    public record SubjectResponse(
            Notification.ResponseStatus status,
            String answer,
            OffsetDateTime respondedAt
    ) {
    }

    /** 가장 최근 발생한 이상 징후. 이벤트가 없으면 null. */
    public record LastDetection(
            String eventId,
            String eventType,
            String applianceType,
            String description,
            OffsetDateTime occurredAt,
            Map<String, Object> reason
    ) {
    }
}
