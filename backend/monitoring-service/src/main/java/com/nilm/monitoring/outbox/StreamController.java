package com.nilm.monitoring.outbox;

import com.nilm.monitoring.security.CurrentUserService;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/monitoring/stream")
public class StreamController {

    private final CurrentUserService currentUserService;
    private final SseHub sseHub;
    private final UiOutboxRepository outboxRepository;

    public StreamController(
            CurrentUserService currentUserService,
            SseHub sseHub,
            UiOutboxRepository outboxRepository) {
        this.currentUserService = currentUserService;
        this.sseHub = sseHub;
        this.outboxRepository = outboxRepository;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(name = "Last-Event-ID", required = false) Long lastEventId) {
        String userId = currentUserService.userId();
        boolean admin = currentUserService.isAdmin();
        SseEmitter emitter = sseHub.register(userId, admin);
        if (lastEventId != null) {
            List<UiOutbox> replay = admin
                    ? outboxRepository.findByOutboxIdGreaterThanOrderByOutboxId(
                            lastEventId, PageRequest.of(0, 1000))
                    : outboxRepository.findReplayForUser(
                            userId, lastEventId, PageRequest.of(0, 1000));
            replay.forEach(event -> sseHub.send(emitter, event));
        }
        return emitter;
    }
}
