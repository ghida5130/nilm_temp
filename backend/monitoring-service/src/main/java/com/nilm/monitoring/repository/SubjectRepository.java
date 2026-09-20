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

    /** 타이머 평가가 도는 순서. id 순으로 고정해 순회 결과를 재현 가능하게 둔다. */
    List<Subject> findAllByOrderByIdAsc();

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

    /**
     * monitoring_enabled 캐시가 외출 구간과 어긋난 대상자만 찾는다.
     * 예약된 외출의 시작·종료를 발효시키는 스케줄러가 쓴다.
     */
    @Query("""
            select s from Subject s
            where (s.monitoringEnabled = true
                   and s.awayStartedAt is not null
                   and s.awayStartedAt <= :now
                   and (s.awayUntil is null or s.awayUntil > :now))
               or (s.monitoringEnabled = false
                   and (s.awayStartedAt is null
                        or s.awayStartedAt > :now
                        or (s.awayUntil is not null and s.awayUntil <= :now)))
            """)
    List<Subject> findAwayTransitionCandidates(@Param("now") OffsetDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subject s where s.householdId = :householdId order by s.id")
    List<Subject> findHouseholdForUpdate(@Param("householdId") String householdId);
}

