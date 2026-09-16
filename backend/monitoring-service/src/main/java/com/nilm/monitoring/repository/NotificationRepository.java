package com.nilm.monitoring.repository;

import com.nilm.monitoring.domain.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

// 동시 응답 -> 비관적락
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id")
    Optional<Notification> findForResponse(@Param("id") Long id);

    @Modifying
    @Query("""
            update Notification n
            set n.responseStatus = :expired
            where n.responseStatus = :pending
              and n.responseDeadline <= :now
            """)
    int expireOverdue(
            @Param("pending") Notification.ResponseStatus pending,
            @Param("expired") Notification.ResponseStatus expired,
            @Param("now") OffsetDateTime now
    );
}
