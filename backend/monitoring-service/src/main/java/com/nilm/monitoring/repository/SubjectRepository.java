package com.nilm.monitoring.repository;
import com.nilm.monitoring.domain.Subject;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    boolean existsByHouseholdId(String householdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subject s where s.householdId = :householdId order by s.id")
    List<Subject> findHouseholdForUpdate(@Param("householdId") String householdId);
}

