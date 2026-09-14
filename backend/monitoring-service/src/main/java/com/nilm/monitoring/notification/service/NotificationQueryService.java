package com.nilm.monitoring.notification.service;

import com.nilm.monitoring.common.ResourceNotFoundException;
import com.nilm.monitoring.domain.NotificationDelivery;
import com.nilm.monitoring.notification.dto.NotificationDetailResponse;
import com.nilm.monitoring.notification.repository.NotificationDeliveryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationQueryService {
    private final NotificationDeliveryRepository repository;
    private final Clock clock;

    public NotificationQueryService(NotificationDeliveryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationDetailResponse get(UUID id, String userId) {
        NotificationDelivery delivery = repository.findById(id)
                .filter(candidate -> candidate.getRecipientUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("알림을 찾을 수 없습니다."));
        return NotificationDetailResponse.from(delivery, Instant.now(clock));
    }
}
