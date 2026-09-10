package com.nilm.monitoring.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.web-push.enabled", havingValue = "false", matchIfMissing = true)
public class NoopWebPushClient implements WebPushClient {

    @Override
    public PushResult send(PushSubscription subscription, byte[] payload) {
        return PushResult.rejected(0, "Web Push is disabled");
    }
}
