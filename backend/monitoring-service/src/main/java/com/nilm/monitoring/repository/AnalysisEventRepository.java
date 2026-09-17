package com.nilm.monitoring.repository;
import com.nilm.monitoring.domain.AnalysisEvent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AnalysisEventRepository extends JpaRepository<AnalysisEvent, UUID> {
    List<AnalysisEvent> findAllBySubjectIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            Long subjectId,
            OffsetDateTime from,
            OffsetDateTime until
    );
}

