from datetime import datetime, timedelta, timezone
from uuid import UUID

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session, sessionmaker

from realtime_analysis.database import Base
from realtime_analysis.models import HouseholdOutingPeriod, HouseholdOutingState
from realtime_analysis.outing_repository import SqlAlchemyOutingStateRepository
from realtime_analysis.schemas import OutingEvent


STARTED_AT = datetime(2026, 9, 19, 1, 0, tzinfo=timezone.utc)
ENDED_AT = datetime(2026, 9, 19, 6, 0, tzinfo=timezone.utc)
UPDATED_AT = datetime(2026, 9, 19, 6, 0, 1, tzinfo=timezone.utc)
START_EVENT_ID = UUID("91b7ab17-3c13-51fe-95ec-3e421ab284f1")
END_EVENT_ID = UUID("2f051fe1-7a2a-58f4-b957-1d9b16dad024")


@pytest.fixture
def session_factory() -> sessionmaker[Session]:
    engine = create_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.tables["household_outing_period"].create(engine)
    Base.metadata.tables["household_outing_state"].create(engine)
    return sessionmaker(bind=engine, expire_on_commit=False)


def outing_event(
    event_id: UUID,
    event_type: str,
    occurred_at: datetime,
    household_id: str = "H001",
) -> OutingEvent:
    return OutingEvent.model_validate(
        {
            "event_id": event_id,
            "household_id": household_id,
            "event_type": event_type,
            "occurred_at": occurred_at,
        }
    )


def test_started_event_creates_current_state(
    session_factory: sessionmaker[Session],
) -> None:
    repository = SqlAlchemyOutingStateRepository(session_factory)

    applied = repository.apply_event(
        outing_event(START_EVENT_ID, "OUTING_STARTED", STARTED_AT),
        UPDATED_AT,
    )

    assert applied is True
    state = repository.get_state("H001")
    assert state is not None
    assert state.is_outing is True
    assert _as_utc(state.outing_started_at) == STARTED_AT
    assert state.last_returned_at is None
    assert state.last_event_id == START_EVENT_ID
    with session_factory() as session:
        period = session.get(HouseholdOutingPeriod, START_EVENT_ID)
        assert period is not None
        assert _as_utc(period.started_at) == STARTED_AT
        assert period.ended_at is None


def test_ended_event_records_return_time(
    session_factory: sessionmaker[Session],
) -> None:
    repository = SqlAlchemyOutingStateRepository(session_factory)
    repository.apply_event(
        outing_event(START_EVENT_ID, "OUTING_STARTED", STARTED_AT),
        UPDATED_AT,
    )

    applied = repository.apply_event(
        outing_event(END_EVENT_ID, "OUTING_ENDED", ENDED_AT),
        UPDATED_AT + timedelta(hours=5),
    )

    assert applied is True
    state = repository.get_state("H001")
    assert state is not None
    assert state.is_outing is False
    assert state.outing_started_at is None
    assert _as_utc(state.last_returned_at) == ENDED_AT
    assert state.last_event_id == END_EVENT_ID
    with session_factory() as session:
        period = session.get(HouseholdOutingPeriod, START_EVENT_ID)
        assert period is not None
        assert period.ended_event_id == END_EVENT_ID
        assert _as_utc(period.ended_at) == ENDED_AT


def test_duplicate_event_is_ignored(
    session_factory: sessionmaker[Session],
) -> None:
    repository = SqlAlchemyOutingStateRepository(session_factory)
    event = outing_event(START_EVENT_ID, "OUTING_STARTED", STARTED_AT)

    assert repository.apply_event(event, UPDATED_AT) is True
    assert repository.apply_event(event, UPDATED_AT + timedelta(minutes=1)) is False

    with session_factory() as session:
        assert len(session.scalars(select(HouseholdOutingState)).all()) == 1


def test_older_event_does_not_rewind_current_state(
    session_factory: sessionmaker[Session],
) -> None:
    repository = SqlAlchemyOutingStateRepository(session_factory)
    repository.apply_event(
        outing_event(END_EVENT_ID, "OUTING_ENDED", ENDED_AT),
        UPDATED_AT,
    )

    older_event_id = UUID("a31e6453-46cd-5ed0-bc62-5ed909439606")
    applied = repository.apply_event(
        outing_event(
            older_event_id,
            "OUTING_STARTED",
            STARTED_AT,
        ),
        UPDATED_AT + timedelta(minutes=1),
    )

    assert applied is False
    state = repository.get_state("H001")
    assert state is not None
    assert state.is_outing is False
    assert state.last_event_id == END_EVENT_ID


def test_state_is_independent_for_each_household(
    session_factory: sessionmaker[Session],
) -> None:
    repository = SqlAlchemyOutingStateRepository(session_factory)
    repository.apply_event(
        outing_event(START_EVENT_ID, "OUTING_STARTED", STARTED_AT),
        UPDATED_AT,
    )
    repository.apply_event(
        outing_event(END_EVENT_ID, "OUTING_ENDED", ENDED_AT, "H002"),
        UPDATED_AT,
    )

    assert repository.get_state("H001").is_outing is True  # type: ignore[union-attr]
    assert repository.get_state("H002").is_outing is False  # type: ignore[union-attr]


def test_completed_outing_period_is_found_only_for_overlapping_window(
    session_factory: sessionmaker[Session],
) -> None:
    repository = SqlAlchemyOutingStateRepository(session_factory)
    repository.apply_event(
        outing_event(START_EVENT_ID, "OUTING_STARTED", STARTED_AT),
        UPDATED_AT,
    )
    repository.apply_event(
        outing_event(END_EVENT_ID, "OUTING_ENDED", ENDED_AT),
        UPDATED_AT + timedelta(hours=5),
    )

    assert repository.has_outing_overlap(
        "H001",
        STARTED_AT - timedelta(minutes=1),
        STARTED_AT + timedelta(minutes=1),
    ) is True
    assert repository.has_outing_overlap(
        "H001",
        ENDED_AT,
        ENDED_AT + timedelta(hours=1),
    ) is False


def _as_utc(value: datetime | None) -> datetime | None:
    if value is None:
        return None
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)
