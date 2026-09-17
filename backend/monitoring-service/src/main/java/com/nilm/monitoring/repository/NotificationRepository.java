package com.nilm.monitoring.repository;

import com.nilm.monitoring.domain.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

// 동시 응답 -> 비관적락
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query(value = """
            select n.*
            from notifications n
            join analysis_events e on e.id = n.event_id
            where e.subject_id = :subjectId
            order by n.id desc
            limit 1
            """, nativeQuery = true)
    Optional<Notification> findLatestAlertBySubjectId(@Param("subjectId") Long subjectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id")
    Optional<Notification> findForResponse(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Notification> findAllByResponseStatusAndResponseDeadlineLessThanEqual(
            Notification.ResponseStatus status,
            OffsetDateTime now
    );
}
