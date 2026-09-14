package com.nilm.monitoring.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.nilm.monitoring.notification.dto.SubscriptionCommand;
import com.nilm.monitoring.notification.repository.PushSubscriptionRepository;
import java.time.Clock;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PushSubscriptionValidationTest {
    private final PushSubscriptionService service = new PushSubscriptionService(
            mock(PushSubscriptionRepository.class), Clock.systemUTC());

    @Test
    void rejectsPrivateNetworkEndpoint() {
        assertThatThrownBy(() -> service.subscribe("user", command("https://127.0.0.1/push")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local network");
    }

    @Test
    void rejectsInvalidWebPushPublicKey() {
        String shortKey = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[64]);
        SubscriptionCommand command = new SubscriptionCommand("https://push.example.com/subscription",
                shortKey, encoded(new byte[16]), null);
        assertThatThrownBy(() -> service.subscribe("user", command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("p256dh");
    }

    private static SubscriptionCommand command(String endpoint) {
        byte[] publicKey = new byte[65];
        publicKey[0] = 0x04;
        return new SubscriptionCommand(endpoint, encoded(publicKey), encoded(new byte[16]), null);
    }

    private static String encoded(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
