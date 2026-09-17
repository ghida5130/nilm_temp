package com.nilm.monitoring.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.config.enums.ApplianceType;
import com.nilm.monitoring.domain.AnalysisEvent;
import com.nilm.monitoring.domain.Notification;
import com.nilm.monitoring.domain.Subject;
import com.nilm.monitoring.dto.SubjectEventsResponse;
import com.nilm.monitoring.repository.AnalysisEventRepository;
import com.nilm.monitoring.repository.NotificationRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 대상자 상세 화면의 이상 징후 기록 영역.
 * 발생 시각 최신순으로 위험 이벤트를 읽고, 연결된 알림의 처리 상태를 함께 붙인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectEventService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int MAX_RANGE_DAYS = 90;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String CURSOR_SEPARATOR = "|";

    /**
     * 이벤트 유형별 화면 문구. 분석 서비스가 새 유형을 추가해도 화면이 비지 않도록
     * 표에 없는 값은 일반 문구로 되돌린다.
     */
    private static final Map<String, String> DESCRIPTIONS = Map.of(
            "ROUTINE_MISSED", "%s 미작동 감지"
    );

    private final SubjectAccessGuard accessGuard;
    private final AnalysisEventRepository events;
    private final NotificationRepository notifications;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SubjectEventsResponse getEvents(
            String authSub,
            Long subjectId,
            LocalDate requestedFrom,
            LocalDate requestedTo,
            Integer requestedSize,
            String cursor
    ) {
        Subject subject = accessGuard.requireReadable(authSub, subjectId);

        LocalDate to = requestedTo == null ? LocalDate.now(ZONE) : requestedTo;
        LocalDate from = requestedFrom == null
                ? to.minusDays(DEFAULT_RANGE_DAYS - 1L)
                : requestedFrom;
        validateRange(from, to);

        int size = pageSize(requestedSize);
        OffsetDateTime rangeStart = from.atStartOfDay(ZONE).toOffsetDateTime();
        OffsetDateTime rangeEnd = to.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();

        // 다음 페이지가 있는지 알아내려고 한 건을 더 읽는다.
        List<AnalysisEvent> rows = readPage(
                subject.getId(), rangeStart, rangeEnd, cursor, size + 1);
        boolean hasNext = rows.size() > size;
        List<AnalysisEvent> page = hasNext ? rows.subList(0, size) : rows;

        Map<UUID, Notification> alerts = alertsByEventId(page);
        List<SubjectEventsResponse.EventItem> items = page.stream()
                .map(event -> toItem(event, alerts.get(event.getId())))
                .toList();

        String nextCursor = hasNext
                ? encodeCursor(page.get(page.size() - 1))
                : null;

        return new SubjectEventsResponse(
                subject.getId().toString(),
                from,
                to,
                ZONE.getId(),
                items,
                new SubjectEventsResponse.Pagination(size, hasNext, nextCursor)
        );
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "조회 시작일은 종료일보다 늦을 수 없습니다."
            );
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "조회 기간은 최대 " + MAX_RANGE_DAYS + "일입니다."
            );
        }
    }

    private int pageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (requested < 1 || requested > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다."
            );
        }
        return requested;
    }

    private List<AnalysisEvent> readPage(
            Long subjectId,
            OffsetDateTime rangeStart,
            OffsetDateTime rangeEnd,
            String cursor,
            int limit
    ) {
        if (cursor == null || cursor.isBlank()) {
            return events.findFirstPage(subjectId, rangeStart, rangeEnd, limit);
        }

        Cursor decoded = decodeCursor(cursor);
        return events.findPageAfterCursor(
                subjectId, rangeStart, rangeEnd,
                decoded.occurredAt(), decoded.eventId(), limit);
    }

    private Map<UUID, Notification> alertsByEventId(List<AnalysisEvent> page) {
        if (page.isEmpty()) {
            return Map.of();
        }

        List<UUID> eventIds = page.stream().map(AnalysisEvent::getId).toList();
        Map<UUID, Notification> byEventId = new HashMap<>();
        for (Notification notification : notifications.findAllByEventIdIn(eventIds)) {
            // 같은 이벤트로 알림이 여러 번 생성됐다면 가장 최근 것만 보여준다.
            byEventId.merge(
                    notification.getEventId(),
                    notification,
                    (kept, candidate) ->
                            candidate.getId() > kept.getId() ? candidate : kept
            );
        }
        return byEventId;
    }

    private SubjectEventsResponse.EventItem toItem(
            AnalysisEvent event,
            Notification alert
    ) {
        return new SubjectEventsResponse.EventItem(
                event.getId().toString(),
                event.getEventType(),
                event.getApplianceType(),
                describe(event),
                event.getRiskLevel(),
                event.getRiskScore(),
                event.getOccurredAt(),
                parseReason(event),
                toAlert(alert)
        );
    }

    private SubjectEventsResponse.Alert toAlert(Notification alert) {
        if (alert == null) {
            return null;
        }

        String answer = alert.getUserResponse() == null
                ? null
                : alert.getUserResponse() ? "YES" : "NO";
        return new SubjectEventsResponse.Alert(
                alert.getId().toString(),
                alert.getManagerResponseStatus(),
                alert.getManagerStatusUpdatedAt(),
                new SubjectEventsResponse.SubjectResponse(
                        alert.getResponseStatus(),
                        answer,
                        alert.getRespondedAt()
                )
        );
    }

    private String describe(AnalysisEvent event) {
        String template = event.getEventType() == null
                ? null
                : DESCRIPTIONS.get(event.getEventType());
        if (template == null) {
            return event.getApplianceType() == null || event.getApplianceType().isBlank()
                    ? "이상 징후 감지"
                    : ApplianceType.labelOf(event.getApplianceType()) + " 사용 이상 감지";
        }
        return String.format(template, ApplianceType.labelOf(event.getApplianceType()));
    }

    /**
     * reason은 분석 서비스가 보낸 JSON을 문자열로 보관한다.
     * 이벤트 유형마다 구조가 달라 화면에 그대로 넘기고, 깨진 값이면 원문을 담아 돌려준다.
     */
    private Map<String, Object> parseReason(AnalysisEvent event) {
        String reason = event.getReason();
        if (reason == null || reason.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    reason, new TypeReference<Map<String, Object>>() {
                    });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            log.warn("이벤트 사유를 JSON으로 읽지 못했습니다: eventId={}", event.getId());
            return Map.of("raw", reason);
        }
    }

    private String encodeCursor(AnalysisEvent last) {
        String raw = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(last.getOccurredAt())
                + CURSOR_SEPARATOR
                + last.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        try {
            String raw = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf(CURSOR_SEPARATOR);
            if (separator < 0) {
                throw new IllegalArgumentException(cursor);
            }
            return new Cursor(
                    OffsetDateTime.parse(
                            raw.substring(0, separator),
                            DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    UUID.fromString(raw.substring(separator + 1))
            );
        } catch (RuntimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cursor 값을 읽을 수 없습니다."
            );
        }
    }

    private record Cursor(OffsetDateTime occurredAt, UUID eventId) {
    }
}
