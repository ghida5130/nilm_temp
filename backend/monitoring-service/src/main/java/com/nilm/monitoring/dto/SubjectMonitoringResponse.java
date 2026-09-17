package com.nilm.monitoring.dto;

import com.nilm.monitoring.config.enums.RiskLevel;
import com.nilm.monitoring.domain.Notification;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SubjectMonitoringResponse(List<SubjectSummary> subjects) {

    public record SubjectSummary(
            String subjectId,
            String name,
            int age,
            String address,
            String phone,
            long version,
            RiskLevel riskLevel,
            int riskScore,
            LastActivity lastActivity,
            LatestAlert latestAlert,
            RiskTrend riskTrend,
            OffsetDateTime updatedAt
    ) {
    }

    public record LastActivity(
            OffsetDateTime occurredAt,
            String applianceType
    ) {
    }

    public record LatestAlert(
            String alertId,
            String eventId,
            SubjectResponse subjectResponse
    ) {
    }

    public record SubjectResponse(
            Notification.ResponseStatus status,
            String answer,
            OffsetDateTime respondedAt
    ) {
    }

    public record RiskTrend(
            String timezone,
            List<DailyScore> dailyScores
    ) {
    }

    public record DailyScore(LocalDate date, int score) {
    }
}
