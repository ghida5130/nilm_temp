package com.nilm.monitoring.service;

import com.nilm.monitoring.domain.Notification;
import com.nilm.monitoring.dto.NotificationResponseDto;
import com.nilm.monitoring.dto.NotificationResponseRequest;
import com.nilm.monitoring.repository.NotificationRepository;
import com.nilm.monitoring.repository.AnalysisEventRepository;
import com.nilm.monitoring.repository.SubjectRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final AnalysisEventRepository events;
    private final SubjectRepository subjects;

    @Value("${app.push.test-auth-sub}")
    private String testAuthSub;

    @Transactional
    public NotificationResponseDto respond(
            Long notificationId,
            NotificationResponseRequest request
    ) {
        Notification notification = repository
                .findForResponse(notificationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "알림을 찾을 수 없습니다."
                ));

        boolean answer = request.answer().equals("yes");

        boolean newlyAnswered = notification.getResponseStatus()
                != Notification.ResponseStatus.ANSWERED;
        if (!newlyAnswered) {

            // 동일 응답 재전송은 성공, 다른 응답으로 변경은 거부
            if (!Boolean.valueOf(answer)
                    .equals(notification.getUserResponse())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "이미 다른 응답이 저장되었습니다."
                );
            }
        } else {
            try {
                notification.answer(
                        answer,
                        OffsetDateTime.now(ZoneOffset.UTC)
                );
            } catch (IllegalStateException e) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        e.getMessage()
                );
            }
        }

        if (newlyAnswered && notification.getEventId() != null) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            touchSubject(notification, now);
        }

        return new NotificationResponseDto(
                notification.getId().toString(),
                Boolean.TRUE.equals(notification.getUserResponse())
                        ? "yes" : "no",
                request.respondedAt(),
                request.source()
        );
    }

    @Transactional
    public void expireOverdue() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        var overdue = repository.findAllByResponseStatusAndResponseDeadlineLessThanEqual(
                Notification.ResponseStatus.PENDING,
                now
        );
        for (Notification notification : overdue) {
            notification.expireIfOverdue(now);
            touchSubject(notification, now);
        }
    }

    // 인증 연결 전, 구독 등록과 동일한 테스트 사용자에게 알림 생성
    @Transactional
    public Notification createNotification(UUID eventId, boolean responseRequired) {
        return createNotification(eventId, testAuthSub, responseRequired);
    }

    @Transactional
    public Notification createNotification(
            UUID eventId,
            String recipientAuthSub,
            boolean responseRequired
    ) {
        Notification notification = new Notification(
                eventId,
                recipientAuthSub
        );

        if (responseRequired) {
            notification.requestResponse(
                    OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30)
            );
        }

        return repository.save(notification);
    }

    /**
     * 발송 결과 저장
     * */
    @Transactional
    public void updateSendResult(Long notificationId, boolean success) {
        Notification notification = repository.findForResponse(notificationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "알림을 찾을 수 없습니다."
                ));

        if (success) {
            notification.markSent();
        } else if (notification.getSendStatus()
                != Notification.SendStatus.SENT) {
            notification.markFailed();
        }
    }

    private void touchSubject(Notification notification, OffsetDateTime now) {
        if (notification.getEventId() == null) {
            return;
        }
        events.findById(notification.getEventId())
                .ifPresent(event -> subjects.touchState(event.getSubjectId(), now));
    }

}
