package com.nilm.monitoring.service;

import com.nilm.monitoring.domain.AnalysisEvent;
import com.nilm.monitoring.domain.Notification;
import com.nilm.monitoring.domain.Subject;
import com.nilm.monitoring.dto.SubjectMonitoringResponse;
import com.nilm.monitoring.repository.AnalysisEventRepository;
import com.nilm.monitoring.repository.ManagerRepository;
import com.nilm.monitoring.repository.NotificationRepository;
import com.nilm.monitoring.repository.SubjectRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SubjectMonitoringService {

    private static final ZoneId TREND_ZONE = ZoneId.of("Asia/Seoul");
    private static final int TREND_DAYS = 7;

    private final ManagerRepository managers;
    private final SubjectRepository subjects;
    private final AnalysisEventRepository events;
    private final NotificationRepository notifications;

    @Transactional(readOnly = true)
    public SubjectMonitoringResponse getSubjects(String managerAuthSub) {
        if (managerAuthSub == null || managerAuthSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        var manager = managers.findByAuthSub(managerAuthSub)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "등록된 담당자만 대상자 목록을 조회할 수 있습니다."
                ));

        LocalDate today = LocalDate.now(TREND_ZONE);
        OffsetDateTime from = today.minusDays(TREND_DAYS - 1L)
                .atStartOfDay(TREND_ZONE)
                .toOffsetDateTime();
        OffsetDateTime until = today.plusDays(1)
                .atStartOfDay(TREND_ZONE)
                .toOffsetDateTime();

        List<SubjectMonitoringResponse.SubjectSummary> result =
                subjects.findAllByManagerIdOrderByIdAsc(manager.getId()).stream()
                        .map(subject -> toSummary(subject, today, from, until))
                        .toList();
        return new SubjectMonitoringResponse(result);
    }

    private SubjectMonitoringResponse.SubjectSummary toSummary(
            Subject subject,
            LocalDate today,
            OffsetDateTime from,
            OffsetDateTime until
    ) {
        List<AnalysisEvent> trendEvents = events
                .findAllBySubjectIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                        subject.getId(), from, until);

        SubjectMonitoringResponse.LastActivity lastActivity =
                subject.getLastActivityAt() == null ? null
                        : new SubjectMonitoringResponse.LastActivity(
                                subject.getLastActivityAt(),
                                subject.getLastActivityAppliance()
                        );

        SubjectMonitoringResponse.LatestAlert latestAlert = notifications
                .findLatestAlertBySubjectId(subject.getId())
                .map(this::toLatestAlert)
                .orElse(null);

        return new SubjectMonitoringResponse.SubjectSummary(
                subject.getId().toString(),
                subject.getName(),
                Period.between(subject.getBirthDate(), today).getYears(),
                joinAddress(subject.getAddress(), subject.getAddressDetail()),
                subject.getPhone(),
                subject.getStateVersion(),
                subject.getCurrentRiskLevel(),
                subject.getCurrentRiskScore(),
                lastActivity,
                latestAlert,
                toRiskTrend(today, trendEvents),
                subject.getUpdatedAt()
        );
    }

    private SubjectMonitoringResponse.LatestAlert toLatestAlert(Notification notification) {
        String answer = notification.getUserResponse() == null ? null
                : notification.getUserResponse() ? "YES" : "NO";
        var response = new SubjectMonitoringResponse.SubjectResponse(
                notification.getResponseStatus(),
                answer,
                notification.getRespondedAt()
        );
        return new SubjectMonitoringResponse.LatestAlert(
                notification.getId().toString(),
                notification.getEventId().toString(),
                response
        );
    }

    private SubjectMonitoringResponse.RiskTrend toRiskTrend(
            LocalDate today,
            List<AnalysisEvent> trendEvents
    ) {
        Map<LocalDate, Integer> scores = new LinkedHashMap<>();
        for (int offset = TREND_DAYS - 1; offset >= 0; offset--) {
            scores.put(today.minusDays(offset), 0);
        }
        for (AnalysisEvent event : trendEvents) {
            LocalDate eventDate = event.getOccurredAt().atZoneSameInstant(TREND_ZONE).toLocalDate();
            scores.computeIfPresent(eventDate, (date, current) -> Math.max(current, event.getRiskScore()));
        }
        List<SubjectMonitoringResponse.DailyScore> dailyScores = scores.entrySet().stream()
                .map(entry -> new SubjectMonitoringResponse.DailyScore(
                        entry.getKey(), entry.getValue()))
                .toList();
        return new SubjectMonitoringResponse.RiskTrend(TREND_ZONE.getId(), dailyScores);
    }

    private String joinAddress(String address, String detail) {
        if (detail == null || detail.isBlank()) {
            return address;
        }
        return address + " " + detail;
    }
}
