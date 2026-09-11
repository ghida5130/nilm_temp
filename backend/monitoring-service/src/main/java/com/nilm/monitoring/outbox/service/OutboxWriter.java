package com.nilm.monitoring.outbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nilm.monitoring.domain.UiOutbox;
import com.nilm.monitoring.outbox.repository.UiOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class OutboxWriter {

    private final UiOutboxRepository repository;
    private final Clock clock;

    public OutboxWriter(UiOutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public UiOutbox write(String householdId, String eventName, JsonNode payload) {
        return repository.save(new UiOutbox(
                householdId, eventName, payload, Instant.now(clock)));
    }
}
