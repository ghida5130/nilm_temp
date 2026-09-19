"""Persistence for each household's latest outing state."""

from datetime import datetime, timezone
from typing import Protocol

from sqlalchemy import or_, select
from sqlalchemy.orm import Session, sessionmaker

from realtime_analysis.models import HouseholdOutingPeriod, HouseholdOutingState
from realtime_analysis.schemas import OutingEvent


class OutingStateProvider(Protocol):
    """Read contract used by outing-aware pattern detection."""

    def get_state(self, household_id: str) -> HouseholdOutingState | None: ...

    def has_outing_overlap(
        self,
        household_id: str,
        started_at: datetime,
        ended_at: datetime,
    ) -> bool: ...


class OutingStateRepository(OutingStateProvider, Protocol):
    """Read/write contract used by the outing event consumer."""

    def apply_event(
        self,
        event: OutingEvent,
        updated_at: datetime | None = None,
    ) -> bool: ...

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
        applied_at = _as_utc(applied_at)

        with self._session_factory.begin() as session:
            state = session.scalar(
                select(HouseholdOutingState)
                .where(HouseholdOutingState.household_id == event.household_id)
                .with_for_update()
            )
            if state is None:
                session.add(self._initial_state(event, applied_at))
                if event.event_type == "OUTING_STARTED":
                    self._start_period(session, event, applied_at)
            elif (
                event.event_id == state.last_event_id
                or _as_utc(event.occurred_at) <= _as_utc(state.last_event_at)
            ):
                return False
            else:
                if event.event_type == "OUTING_STARTED" and not state.is_outing:
                    self._start_period(session, event, applied_at)
                elif event.event_type == "OUTING_ENDED" and state.is_outing:
                    self._end_open_period(session, event, applied_at)
                self._apply_event(state, event, applied_at)

        return True

    def get_state(self, household_id: str) -> HouseholdOutingState | None:
        with self._session_factory() as session:
            return session.get(HouseholdOutingState, household_id)

    def has_outing_overlap(
        self,
        household_id: str,
        started_at: datetime,
        ended_at: datetime,
    ) -> bool:
        if started_at.tzinfo is None or started_at.utcoffset() is None:
            raise ValueError("started_at must include a timezone")
        if ended_at.tzinfo is None or ended_at.utcoffset() is None:
            raise ValueError("ended_at must include a timezone")
        window_start = _as_utc(started_at)
        window_end = _as_utc(ended_at)
        if window_end < window_start:
            raise ValueError("ended_at must not be earlier than started_at")

        with self._session_factory() as session:
            period = session.scalar(
                select(HouseholdOutingPeriod.started_event_id)
                .where(
                    HouseholdOutingPeriod.household_id == household_id,
                    HouseholdOutingPeriod.started_at <= window_end,
                    or_(
                        HouseholdOutingPeriod.ended_at.is_(None),
                        HouseholdOutingPeriod.ended_at > window_start,
                    ),
                )
                .limit(1)
            )
            return period is not None

    @staticmethod
    def _start_period(
        session: Session,
        event: OutingEvent,
        updated_at: datetime,
    ) -> None:
        occurred_at = _as_utc(event.occurred_at)
        session.add(
            HouseholdOutingPeriod(
                started_event_id=event.event_id,
                household_id=event.household_id,
                ended_event_id=None,
                started_at=occurred_at,
                ended_at=None,
                updated_at=updated_at,
            )
        )

    @staticmethod
    def _end_open_period(
        session: Session,
        event: OutingEvent,
        updated_at: datetime,
    ) -> None:
        period = session.scalar(
            select(HouseholdOutingPeriod)
            .where(
                HouseholdOutingPeriod.household_id == event.household_id,
                HouseholdOutingPeriod.ended_at.is_(None),
            )
            .with_for_update()
        )
        if period is None:
            return
        period.ended_event_id = event.event_id
        period.ended_at = _as_utc(event.occurred_at)
        period.updated_at = updated_at

    @staticmethod
    def _initial_state(
        event: OutingEvent,
        updated_at: datetime,
    ) -> HouseholdOutingState:
        is_outing = event.event_type == "OUTING_STARTED"
        occurred_at = _as_utc(event.occurred_at)
        return HouseholdOutingState(
            household_id=event.household_id,
            is_outing=is_outing,
            outing_started_at=occurred_at if is_outing else None,
            last_returned_at=(
                occurred_at if event.event_type == "OUTING_ENDED" else None
            ),
            last_event_id=event.event_id,
            last_event_at=occurred_at,
            updated_at=updated_at,
        )

    @staticmethod
    def _apply_event(
        state: HouseholdOutingState,
        event: OutingEvent,
        updated_at: datetime,
    ) -> None:
        occurred_at = _as_utc(event.occurred_at)
        if event.event_type == "OUTING_STARTED":
            state.is_outing = True
            if state.outing_started_at is None:
                state.outing_started_at = occurred_at
        else:
            state.is_outing = False
            state.outing_started_at = None
            state.last_returned_at = occurred_at

        state.last_event_id = event.event_id
        state.last_event_at = occurred_at
        state.updated_at = updated_at


def _as_utc(value: datetime) -> datetime:
    """Normalize timestamps returned by drivers that omit the stored UTC tzinfo."""

    if value.tzinfo is None or value.utcoffset() is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)
