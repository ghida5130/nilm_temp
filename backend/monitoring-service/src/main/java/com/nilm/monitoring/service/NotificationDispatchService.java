package com.nilm.monitoring.service;

import com.nilm.monitoring.domain.Notification;
import com.nilm.monitoring.repository.NotificationRepository;
import com.nilm.monitoring.repository.PushSubscriptionRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
public class NotificationDispatchService {
    private final NotificationRepository notifications;
    private final PushSubscriptionRepository subscriptions;
    private final NotificationService notificationService;
    private final WebPushSender sender;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> dispatch(NotificationReady request) {
        Notification notification = notifications.findById(request.notificationId())
                .orElseThrow(() -> new IllegalArgumentException("알림이 없습니다."));
        var targets = subscriptions.findAllByAuthSub(notification.getAuthSub());
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", request.title());
        payload.put("body", request.body());
        payload.put("url", "/");
        if (notification.getResponseStatus() == Notification.ResponseStatus.PENDING) {
            payload.put("notificationId", notification.getId().toString());
            payload.put("expiresAt", notification.getResponseDeadline().toString());
        }
        int accepted = 0;
        for (var target : targets) {
            long ttl = notification.getResponseDeadline() == null ? 300 :
                    Duration.between(OffsetDateTime.now(ZoneOffset.UTC),
                            notification.getResponseDeadline()).getSeconds();
            if (ttl <= 0) break;
            try {
                int status = sender.send(target, payload, (int) Math.min(ttl, 300));
                if (status >= 200 && status < 300) accepted++;
                else if (status == 404 || status == 410) subscriptions.deleteById(target.getId());
                else log.warn("푸시 접수 실패: subscriptionId={}, status={}", target.getId(), status);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("푸시 전송 실패: subscriptionId={}, errorType={}",
                        target.getId(), e.getClass().getSimpleName());
            }
        }
        notificationService.updateSendResult(notification.getId(), accepted > 0);
        return Map.of("notificationId", notification.getId(),
                "subscriptionCount", targets.size(), "acceptedCount", accepted);
    }
}

