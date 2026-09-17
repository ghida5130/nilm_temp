package com.nilm.monitoring.repository;
import com.nilm.monitoring.domain.Subject;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    boolean existsByHouseholdId(String householdId);

    List<Subject> findAllByManagerIdOrderByIdAsc(Long managerId);

    Optional<Subject> findByAuthSub(String authSub);

    @Modifying
    @Query("""
            update Subject s
            set s.stateVersion = s.stateVersion + 1,
                s.updatedAt = :updatedAt
            where s.id = :subjectId
            """)
    int touchState(
            @Param("subjectId") Long subjectId,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subject s where s.householdId = :householdId order by s.id")
    List<Subject> findHouseholdForUpdate(@Param("householdId") String householdId);
}

