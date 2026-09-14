package com.nilm.monitoring.incident.dto;

import com.nilm.monitoring.domain.ActionType;
import com.nilm.monitoring.domain.ActorType;
import com.nilm.monitoring.domain.IncidentAction;
import com.nilm.monitoring.domain.IncidentStatus;
import java.time.Instant;

public record ActionSummary(
        long id,
        ActionType type,
        ActorType actorType,
        String actorId,
        IncidentStatus previousStatus,
        IncidentStatus nextStatus,
        Instant occurredAt
) {
    public static ActionSummary from(IncidentAction action) {
        return new ActionSummary(
                action.getActionId(), action.getActionType(), action.getActorType(), action.getActorId(),
                action.getPreviousStatus(), action.getNextStatus(), action.getOccurredAt());
    }
}
