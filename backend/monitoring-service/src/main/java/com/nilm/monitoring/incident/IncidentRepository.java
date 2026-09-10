package com.nilm.monitoring.incident;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incident i where i.incidentId = :incidentId")
    Optional<Incident> findByIdForUpdate(@Param("incidentId") UUID incidentId);

    @Query("""
            select i from Incident i
             where exists (
                 select h.id from HouseholdAccess h
                  where h.householdId = i.householdId
                    and h.userId = :userId
             )
               and (:status is null or i.status = :status)
             order by i.openedAt desc
            """)
    List<Incident> findVisibleTo(@Param("userId") String userId,
                                 @Param("status") IncidentStatus status);
}
