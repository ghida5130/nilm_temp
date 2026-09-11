package com.nilm.monitoring.notification.service;

import com.nilm.monitoring.domain.PushSubscription;
import com.nilm.monitoring.notification.dto.SubscriptionCommand;
import com.nilm.monitoring.notification.repository.PushSubscriptionRepository;

import java.time.Clock;
import java.time.Instant;
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
        Instant now = Instant.now(clock);
        PushSubscription subscription = repository.findByEndpoint(command.endpoint())
                .map(existing -> {
                    existing.refresh(userId, command.p256dh(), command.auth(), command.expirationTime(), now);
                    return existing;
                })
                .orElseGet(() -> new PushSubscription(
                        userId, command.endpoint(), command.p256dh(), command.auth(),
                        command.expirationTime(), now));
        return repository.save(subscription);
    }
}
