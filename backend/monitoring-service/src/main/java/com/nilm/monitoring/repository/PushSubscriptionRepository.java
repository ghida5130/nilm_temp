package com.nilm.monitoring.repository;

import com.nilm.monitoring.domain.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    // ON CONFLICT (endpoint)
    // 같은 사용자 + 같은 endpoint -> 키 갱신
    // 다른 사용자 + 같은 endpoint -> 변경 X
    @Modifying
    @Query(value = """
            INSERT INTO push_subscriptions (
                auth_sub,
                endpoint,
                p256dh,
                auth
            )
            VALUES (
                :authSub,
                :endpoint,
                :p256dh,
                :auth
            )
            ON CONFLICT (endpoint)
            DO UPDATE SET
                p256dh = EXCLUDED.p256dh,
                auth = EXCLUDED.auth
            WHERE push_subscriptions.auth_sub = EXCLUDED.auth_sub
            """, nativeQuery = true)
    int register(
            @Param("authSub") String authSub,
            @Param("endpoint") String endpoint,
            @Param("p256dh") String p256dh,
            @Param("auth") String auth
    );
}
