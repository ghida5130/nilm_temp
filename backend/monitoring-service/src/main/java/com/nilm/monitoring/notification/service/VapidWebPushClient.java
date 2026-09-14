package com.nilm.monitoring.notification.service;

import com.nilm.monitoring.domain.PushSubscription;

import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.Security;

@Component
@ConditionalOnProperty(name = "app.web-push.enabled", havingValue = "true")
public class VapidWebPushClient implements WebPushClient {

    private static final Logger log = LoggerFactory.getLogger(VapidWebPushClient.class);

    private final PushService pushService;

    public VapidWebPushClient(
            @Value("${app.web-push.vapid-public-key}") String publicKey,
            @Value("${app.web-push.vapid-private-key}") String privateKey,
            @Value("${app.web-push.subject}") String subject) throws Exception {
        if (publicKey.isBlank() || privateKey.isBlank() || subject.isBlank()) {
            throw new IllegalStateException("Web Push is enabled but VAPID configuration is incomplete");
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.pushService = new PushService(publicKey, privateKey, subject);
    }

    @Override
    public PushResult send(PushSubscription subscription, byte[] payload) {
        try {
            var notification = new nl.martijndwars.webpush.Notification(
                    subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth(), payload);
            HttpResponse response = pushService.send(notification);
            try {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    return PushResult.accepted(status);
                }
                return PushResult.rejected(status, "Push provider returned HTTP " + status);
            } finally {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        } catch (Exception exception) {
            log.warn("Web Push request failed for subscription {}", subscription.getSubscriptionId(), exception);
            return PushResult.rejected(0, exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage());
        }
    }
}
