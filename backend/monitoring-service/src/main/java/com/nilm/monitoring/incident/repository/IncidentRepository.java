package com.nilm.monitoring.incident.repository;

import com.nilm.monitoring.domain.Incident;
import com.nilm.monitoring.domain.IncidentStatus;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    long countBySubjectId(Long subjectId);
    Optional<Incident> findBySourceEventId(UUID sourceEventId);

    @Query("select count(i) from Incident i where i.subjectId = :subjectId and i.status in "
            + "(com.nilm.monitoring.domain.IncidentStatus.OPEN, com.nilm.monitoring.domain.IncidentStatus.ACKNOWLEDGED)")
    long countOpenBySubjectId(@Param("subjectId") Long subjectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incident i where i.incidentId = :incidentId")
    Optional<Incident> findByIdForUpdate(@Param("incidentId") UUID incidentId);

    @Query("""
            select i from Incident i
             where (exists (
                 select h.id from HouseholdAccess h
                  where h.householdId = i.householdId
                    and h.userId = :userId
             )
               or exists (
                 select a.id from StaffSubjectAssignment a, StaffProfile p, CareSubject s
                  where a.staffId = p.id and a.subjectId = s.id
                    and p.authSub = :userId
                    and p.status = com.nilm.monitoring.domain.StaffStatus.ACTIVE
                    and a.unassignedAt is null
                    and s.householdId = i.householdId
               ))
               and (:status is null or i.status = :status)
             order by i.openedAt desc
            """)
    List<Incident> findVisibleTo(@Param("userId") String userId,
                                 @Param("status") IncidentStatus status);
}
