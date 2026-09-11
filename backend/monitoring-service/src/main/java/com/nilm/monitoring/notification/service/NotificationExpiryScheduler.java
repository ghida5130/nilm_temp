package com.nilm.monitoring.notification.service;

import com.nilm.monitoring.domain.NotificationDelivery;
import com.nilm.monitoring.notification.repository.NotificationDeliveryRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationExpiryScheduler {

    private final NotificationDeliveryRepository notificationRepository;
    private final NotificationResponseService responseService;
    private final Clock clock;

    public NotificationExpiryScheduler(
            NotificationDeliveryRepository notificationRepository,
            NotificationResponseService responseService,
            Clock clock) {
        this.notificationRepository = notificationRepository;
        this.responseService = responseService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.notification.expiry-delay-ms:5000}")
    public void expire() {
        List<UUID> expiredIds = notificationRepository
                .findExpired(Instant.now(clock), PageRequest.of(0, 100))
                .stream().map(NotificationDelivery::getId).toList();
        expiredIds.forEach(responseService::respondByTimeout);
    }
}
