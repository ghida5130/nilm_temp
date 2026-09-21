package com.nilm.monitoring.service;

import com.nilm.monitoring.dto.PushSubscriptionRequest;
import com.nilm.monitoring.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;

    @Transactional
    public boolean register(
            String authSub,
            PushSubscriptionRequest request
    ) {
        if (authSub == null || authSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        int affectedRows = repository.register(
                authSub,
                request.endpoint(),
                request.keys().p256dh(),
                request.keys().auth()
        );

        return affectedRows == 1;
    }

}
