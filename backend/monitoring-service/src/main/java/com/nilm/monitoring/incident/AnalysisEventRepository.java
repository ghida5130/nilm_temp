package com.nilm.monitoring.incident;

import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisEventRepository extends JpaRepository<AnalysisEvent, UUID> {

    boolean existsByHouseholdIdAndEventTypeAndEventDateAndExpectedUntil(
            String householdId, String eventType, LocalDate eventDate, LocalTime expectedUntil);
}
