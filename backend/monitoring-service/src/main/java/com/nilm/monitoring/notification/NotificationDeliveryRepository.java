package com.nilm.monitoring.notification;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    List<NotificationDelivery> findByIncidentIdOrderByCreatedAt(UUID incidentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificationDelivery n where n.id = :id")
    Optional<NotificationDelivery> findByIdForUpdate(@Param("id") UUID id);

    List<NotificationDelivery> findByDeliveryStatusOrderByCreatedAt(
            DeliveryStatus deliveryStatus, Pageable pageable);

    @Query("""
            select n from NotificationDelivery n
             where n.deliveryStatus = com.nilm.monitoring.notification.DeliveryStatus.SENT
               and n.answer is null
               and n.responseDeadlineAt <= :now
             order by n.responseDeadlineAt
            """)
    List<NotificationDelivery> findExpired(@Param("now") Instant now, Pageable pageable);
}
