package com.nilm.monitoring.service;

import com.nilm.monitoring.domain.Notification;
import com.nilm.monitoring.dto.NotificationResponseDto;
import com.nilm.monitoring.dto.NotificationResponseRequest;
import com.nilm.monitoring.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;

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

        if (notification.getResponseStatus()
                == Notification.ResponseStatus.ANSWERED) {

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
        repository.expireOverdue(
                Notification.ResponseStatus.PENDING,
                Notification.ResponseStatus.EXPIRED,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
