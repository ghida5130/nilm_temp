package com.nilm.monitoring.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserIdInAndRevokedAtIsNullAndExpirationTimeAfter(
            Collection<String> userIds, Instant now);

    List<PushSubscription> findByUserIdInAndRevokedAtIsNullAndExpirationTimeIsNull(
            Collection<String> userIds);
}
