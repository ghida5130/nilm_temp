package com.nilm.monitoring.subject.repository;

import com.nilm.monitoring.domain.StaffSubjectAssignment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<StaffSubjectAssignment, Long> {
    @Query("select a from StaffSubjectAssignment a where a.staffId = :staffId and a.unassignedAt is null")
    List<StaffSubjectAssignment> findActiveByStaffId(@Param("staffId") Long staffId);

    Optional<StaffSubjectAssignment> findByIdAndStaffId(Long id, Long staffId);
    boolean existsByStaffIdAndSubjectIdAndUnassignedAtIsNull(Long staffId, Long subjectId);
    boolean existsBySubjectIdAndUnassignedAtIsNull(Long subjectId);

    @Query("select case when count(a) > 0 then true else false end from StaffSubjectAssignment a, StaffProfile p, CareSubject s "
            + "where a.staffId = p.id and a.subjectId = s.id and p.authSub = :authSub "
            + "and p.status = com.nilm.monitoring.domain.StaffStatus.ACTIVE and a.unassignedAt is null "
            + "and s.householdId = :householdId")
    boolean existsActiveAccess(@Param("authSub") String authSub, @Param("householdId") String householdId);

    @Query("select distinct p.authSub from StaffSubjectAssignment a, StaffProfile p, CareSubject s "
            + "where a.staffId = p.id and a.subjectId = s.id and p.status = com.nilm.monitoring.domain.StaffStatus.ACTIVE "
            + "and a.unassignedAt is null and s.householdId = :householdId")
    List<String> findActiveAuthSubsByHouseholdId(@Param("householdId") String householdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StaffSubjectAssignment a where a.id = :id and a.staffId = :staffId")
    Optional<StaffSubjectAssignment> findOwnedForUpdate(@Param("id") Long id, @Param("staffId") Long staffId);
}
