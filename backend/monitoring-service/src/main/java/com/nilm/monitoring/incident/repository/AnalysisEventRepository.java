package com.nilm.monitoring.incident.repository;

import com.nilm.monitoring.domain.AnalysisEvent;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisEventRepository extends JpaRepository<AnalysisEvent, UUID> {

    Optional<AnalysisEvent> findFirstBySubjectIdOrderByOccurredAtDescEventIdDesc(Long subjectId);

    @Query("select e from AnalysisEvent e where e.subjectId = :subjectId and e.occurredAt >= :from "
            + "and e.occurredAt < :to order by e.occurredAt desc, e.eventId desc")
    List<AnalysisEvent> findSubjectEvents(@Param("subjectId") Long subjectId,
                                          @Param("from") Instant from, @Param("to") Instant to,
                                          Pageable pageable);
}
