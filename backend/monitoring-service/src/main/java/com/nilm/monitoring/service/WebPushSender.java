package com.nilm.monitoring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.domain.PushSubscription;
import lombok.RequiredArgsConstructor;
import nl.martijndwars.webpush.PushService;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
@RequiredArgsConstructor
public class WebPushSender {

    private final PushService pushService;
    private final ObjectMapper objectMapper;

    public int send(
            PushSubscription subscription,
            Map<String, Object> payload,
            int ttlSeconds
    ) throws Exception {

        // DB 엔티티 Notification과 이름이 같아 전체 패키지명 사용
        var message = new nl.martijndwars.webpush.Notification(
                subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuth(),
                objectMapper.writeValueAsBytes(payload),
                ttlSeconds
        );

        try {
            var response = pushService.send(message);
            return response.getStatusLine().getStatusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
