"""Persistence for each household's latest outing state."""

from datetime import datetime, timezone
from typing import Protocol

from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from realtime_analysis.models import HouseholdOutingState
from realtime_analysis.schemas import OutingEvent


class OutingStateRepository(Protocol):
    """Storage contract used by the future outing event consumer."""

    def apply_event(
        self,
        event: OutingEvent,
        updated_at: datetime | None = None,
    ) -> bool: ...

    def get_state(self, household_id: str) -> HouseholdOutingState | None: ...


class SqlAlchemyOutingStateRepository:
    """Applies only the latest event to one state row per household."""

    def __init__(self, session_factory: sessionmaker[Session]) -> None:
        self._session_factory = session_factory

    def apply_event(
        self,
        event: OutingEvent,
        updated_at: datetime | None = None,
    ) -> bool:
        """Apply a new event and report whether the current state changed."""

        applied_at = updated_at or datetime.now(timezone.utc)
        if applied_at.tzinfo is None or applied_at.utcoffset() is None:
            raise ValueError("updated_at must include a timezone")

        with self._session_factory.begin() as session:
            state = session.scalar(
                select(HouseholdOutingState)
                .where(HouseholdOutingState.household_id == event.household_id)
                .with_for_update()
            )
            if state is None:
                session.add(self._initial_state(event, applied_at))
            elif (
                event.event_id == state.last_event_id
                or _as_utc(event.occurred_at) <= _as_utc(state.last_event_at)
            ):
                return False
            else:
                self._apply_event(state, event, applied_at)

        return True

    def get_state(self, household_id: str) -> HouseholdOutingState | None:
        with self._session_factory() as session:
            return session.get(HouseholdOutingState, household_id)

    @staticmethod
    def _initial_state(
        event: OutingEvent,
        updated_at: datetime,
    ) -> HouseholdOutingState:
        is_outing = event.event_type == "OUTING_STARTED"
        return HouseholdOutingState(
            household_id=event.household_id,
            is_outing=is_outing,
            outing_started_at=event.occurred_at if is_outing else None,
            last_returned_at=(
                event.occurred_at if event.event_type == "OUTING_ENDED" else None
            ),
            last_event_id=event.event_id,
            last_event_at=event.occurred_at,
            updated_at=updated_at,
        )

    @staticmethod
    def _apply_event(
        state: HouseholdOutingState,
        event: OutingEvent,
        updated_at: datetime,
    ) -> None:
        if event.event_type == "OUTING_STARTED":
            state.is_outing = True
            state.outing_started_at = event.occurred_at
        else:
            state.is_outing = False
            state.outing_started_at = None
            state.last_returned_at = event.occurred_at

        state.last_event_id = event.event_id
        state.last_event_at = event.occurred_at
        state.updated_at = updated_at


def _as_utc(value: datetime) -> datetime:
    """Normalize timestamps returned by drivers that omit the stored UTC tzinfo."""

    if value.tzinfo is None or value.utcoffset() is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)
