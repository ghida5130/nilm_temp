package com.nilm.monitoring.outbox.service;

import com.nilm.monitoring.incident.repository.HouseholdAccessRepository;
import com.nilm.monitoring.domain.UiOutbox;
import com.nilm.monitoring.outbox.repository.UiOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UiOutboxDispatcher {

    private final UiOutboxRepository repository;
    private final HouseholdAccessRepository accessRepository;
    private final SseHub sseHub;
    private final Clock clock;

    public UiOutboxDispatcher(
            UiOutboxRepository repository,
            HouseholdAccessRepository accessRepository,
            SseHub sseHub,
            Clock clock) {
        this.repository = repository;
        this.accessRepository = accessRepository;
        this.sseHub = sseHub;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.dispatch-delay-ms:500}")
    public void dispatch() {
        List<UiOutbox> events = repository.findByPublishedAtIsNullOrderByOutboxId(PageRequest.of(0, 100));
        for (UiOutbox event : events) {
            accessRepository.findUserIdsByHouseholdId(event.getHouseholdId())
                    .forEach(userId -> sseHub.send(userId, event));
            sseHub.sendToAdmins(event);
            event.markPublished(Instant.now(clock));
            repository.save(event);
        }
    }

    @Scheduled(fixedDelayString = "${app.sse.heartbeat-delay-ms:25000}")
    public void heartbeat() {
        sseHub.heartbeat();
    }
}
