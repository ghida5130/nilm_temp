package com.nilm.monitoring.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 알림 발송 Entity
 * 알림 응답과 상태, 기한 관리
 * */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum SendStatus {
        PENDING,
        SENT,
        FAILED
    }

    public enum ResponseStatus {
        NOT_REQUIRED,
        PENDING,
        ANSWERED,
        EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 일일 요약 알림은 이벤트 없이 생성 가능
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "auth_sub", nullable = false)
    private String authSub;

    @Column(name = "response_deadline")
    private OffsetDateTime responseDeadline;

    @Column(name = "user_response")
    private Boolean userResponse; // 예: true 아니오: false 미응답: null

    @Enumerated(EnumType.STRING)
    @Column(name = "send_status", nullable = false)
    private SendStatus sendStatus = SendStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", nullable = false)
    private ResponseStatus responseStatus = ResponseStatus.NOT_REQUIRED;

    protected Notification() {
    }

    public void requestResponse(OffsetDateTime deadline) {
        if (responseStatus != ResponseStatus.NOT_REQUIRED) {
            throw new IllegalStateException("이미 응답 요청이 설정되었습니다.");
        }
        if (deadline == null) {
            throw new IllegalArgumentException("응답 기한이 필요합니다.");
        }

        this.responseDeadline = deadline;
        this.responseStatus = ResponseStatus.PENDING;
    }

    public void answer(boolean answer, OffsetDateTime now) {
        if (responseStatus != ResponseStatus.PENDING) {
            throw new IllegalStateException("응답 대기 중인 알림이 아닙니다.");
        }
        if (!now.isBefore(responseDeadline)) {
            throw new IllegalStateException("응답 기한이 지났습니다.");
        }

        this.userResponse = answer;
        this.responseStatus = ResponseStatus.ANSWERED;
    }

    public void expireIfOverdue(OffsetDateTime now) {
        if (responseStatus == ResponseStatus.PENDING
                && !now.isBefore(responseDeadline)) {
            this.responseStatus = ResponseStatus.EXPIRED;
        }
    }

    public void markSent() {
        this.sendStatus = SendStatus.SENT;
    }

    public void markFailed() {
        if (sendStatus == SendStatus.SENT) {
            throw new IllegalStateException("발송 완료된 알림입니다.");
        }
        this.sendStatus = SendStatus.FAILED;
    }
}