package com.nilm.monitoring.subject.repository;

import com.nilm.monitoring.domain.CareSubject;
import com.nilm.monitoring.domain.SubjectStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareSubjectRepository extends JpaRepository<CareSubject, Long> {
    Optional<CareSubject> findBySubjectNumber(String subjectNumber);
    List<CareSubject> findByRegionCodeInAndStatusIn(Collection<String> regionCodes,
                                                    Collection<SubjectStatus> statuses);
    List<CareSubject> findByNameBlindIndexAndRegionCodeInAndStatusIn(
            String nameBlindIndex, Collection<String> regionCodes, Collection<SubjectStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CareSubject s where s.id = :id")
    Optional<CareSubject> findByIdForUpdate(@Param("id") Long id);
}
