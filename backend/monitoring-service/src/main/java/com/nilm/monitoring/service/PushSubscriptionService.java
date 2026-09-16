package com.nilm.monitoring.service;

import com.nilm.monitoring.dto.PushSubscriptionRequest;
import com.nilm.monitoring.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;

    @Transactional
    public boolean register(
            String authSub,
            PushSubscriptionRequest request
    ) {
        int affectedRows = repository.register(
                authSub,
                request.endpoint(),
                request.keys().p256dh(),
                request.keys().auth()
        );

        return affectedRows == 1;
    }

}
