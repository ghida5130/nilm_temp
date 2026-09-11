package com.nilm.monitoring.incident.repository;

import com.nilm.monitoring.domain.ActionType;
import com.nilm.monitoring.domain.IncidentAction;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentActionRepository extends JpaRepository<IncidentAction, Long> {

    List<IncidentAction> findByIncidentIdOrderByActionId(UUID incidentId);

    boolean existsByIncidentIdAndActionType(UUID incidentId, ActionType actionType);
}
