package com.nilm.monitoring.outbox.service;

import com.nilm.monitoring.domain.UiOutbox;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseHub {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Set<String> adminUserIds = ConcurrentHashMap.newKeySet();

    public SseEmitter register(String userId, boolean admin) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        if (admin) adminUserIds.add(userId);
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        return emitter;
    }

    public void send(String userId, UiOutbox event) {
        for (SseEmitter emitter : List.copyOf(emitters.getOrDefault(
                userId, new CopyOnWriteArrayList<>()))) {
            try {
                emitter.send(SseEmitter.event()
                        .id(event.getOutboxId().toString())
                        .name(event.getEventName())
                        .data(event.getPayload(), MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException exception) {
                remove(userId, emitter);
            }
        }
    }

    public void send(SseEmitter emitter, UiOutbox event) {
        try {
            emitter.send(toEvent(event));
        } catch (IOException | IllegalStateException exception) {
            emitter.completeWithError(exception);
        }
    }

    public void sendToAdmins(UiOutbox event) {
        adminUserIds.forEach(userId -> send(userId, event));
    }

    public void heartbeat() {
        emitters.forEach((userId, userEmitters) -> {
            for (SseEmitter emitter : List.copyOf(userEmitters)) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException exception) {
                    remove(userId, emitter);
                }
            }
        });
    }

    private void remove(String userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) {
            emitters.remove(userId, userEmitters);
            adminUserIds.remove(userId);
        }
    }

    private SseEmitter.SseEventBuilder toEvent(UiOutbox event) {
        return SseEmitter.event()
                .id(event.getOutboxId().toString())
                .name(event.getEventName())
                .data(event.getPayload(), MediaType.APPLICATION_JSON);
    }
}
