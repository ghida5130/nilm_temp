package com.nilm.monitoring.notification.service;

import com.nilm.monitoring.domain.PushSubscription;
import com.nilm.monitoring.notification.dto.SubscriptionCommand;
import com.nilm.monitoring.notification.repository.PushSubscriptionRepository;
import com.nilm.monitoring.common.ConflictException;
import com.nilm.monitoring.common.ResourceNotFoundException;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final Clock clock;

    public PushSubscriptionService(PushSubscriptionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public PushSubscription subscribe(String userId, SubscriptionCommand command) {
        validate(command);
        Instant now = Instant.now(clock);
        PushSubscription subscription = repository.findByEndpoint(command.endpoint())
                .map(existing -> {
                    if (!existing.getUserId().equals(userId)) {
                        throw new ConflictException("다른 계정에 등록된 Push 구독입니다.");
                    }
                    existing.refresh(userId, command.p256dh(), command.auth(), command.expirationTime(), now);
                    return existing;
                })
                .orElseGet(() -> new PushSubscription(
                        userId, command.endpoint(), command.p256dh(), command.auth(),
                        command.expirationTime(), now));
        return repository.save(subscription);
    }

    @Transactional
    public void revoke(Long subscriptionId, String userId) {
        PushSubscription subscription = repository.findBySubscriptionIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Push 구독을 찾을 수 없습니다."));
        if (subscription.getRevokedAt() == null) subscription.revoke(Instant.now(clock));
    }

    private static void validate(SubscriptionCommand command) {
        URI endpoint;
        try { endpoint = URI.create(command.endpoint()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("endpoint must be a URI"); }
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("endpoint must be an absolute HTTPS URL");
        }
        rejectLocalEndpoint(endpoint.getHost());
        byte[] publicKey = decode(command.p256dh(), "p256dh", 65);
        if (publicKey[0] != 0x04) throw new IllegalArgumentException("p256dh must be an uncompressed EC key");
        decode(command.auth(), "auth", 16);
    }

    private static byte[] decode(String value, String name, int expectedBytes) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != expectedBytes) throw new IllegalArgumentException(name + " has an invalid length");
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " must be valid base64url", e);
        }
    }

    private static void rejectLocalEndpoint(String host) {
        String value = host.toLowerCase(java.util.Locale.ROOT);
        if (value.equals("localhost") || value.endsWith(".localhost") || value.endsWith(".local")
                || value.equals("0.0.0.0") || value.equals("::") || value.equals("::1")
                || value.startsWith("127.") || value.startsWith("10.") || value.startsWith("192.168.")) {
            throw new IllegalArgumentException("endpoint must not target a local network");
        }
        String[] parts = value.split("\\.");
        if (parts.length == 4) {
            try {
                int first = Integer.parseInt(parts[0]);
                int second = Integer.parseInt(parts[1]);
                if (first == 169 && second == 254 || first == 172 && second >= 16 && second <= 31) {
                    throw new IllegalArgumentException("endpoint must not target a local network");
                }
            } catch (NumberFormatException ignored) {
                // A public DNS name is validated by the Web Push provider/TLS connection.
            }
        }
    }
}
