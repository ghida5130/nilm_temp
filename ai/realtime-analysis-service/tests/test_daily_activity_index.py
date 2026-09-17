from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from unittest.mock import Mock

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session, sessionmaker

from realtime_analysis.daily_activity_index import (
    DailyActivityIndexCalculator,
    DailyActivityIndexRepository,
    DailyActivityIndexScheduler,
    DailyActivityIndexService,
    SessionSlice,
)
from realtime_analysis.database import Base
from realtime_analysis.models import (
    ApplianceUsageSession,
    HouseholdActivityDaily,
    HouseholdObservationDaily,
)


@pytest.fixture
def session_factory() -> sessionmaker[Session]:
    engine = create_engine("sqlite+pysqlite:///:memory:")
    for table_name in (
        "household_observation_daily",
        "household_activity_daily",
        "appliance_usage_session",
    ):
        Base.metadata.tables[table_name].create(engine)
    return sessionmaker(bind=engine, expire_on_commit=False)


def test_nearby_hysteresis_sessions_are_one_logical_use() -> None:
    calculator = DailyActivityIndexCalculator()
    start = datetime(2026, 9, 16, 1, tzinfo=timezone.utc)

    components = calculator.calculate(
        [
            SessionSlice("MICROWAVE", start, start + timedelta(seconds=30), True),
            SessionSlice(
                "MICROWAVE",
                start + timedelta(seconds=60),
                start + timedelta(seconds=90),
                True,
            ),
        ]
    )

    assert components.usage_count == 1
    assert components.appliance_type_count == 1
    assert components.usage_duration_seconds == 60


def test_duration_score_caps_long_kettle_use_but_keeps_raw_duration() -> None:
    calculator = DailyActivityIndexCalculator()
    start = datetime(2026, 9, 16, 1, tzinfo=timezone.utc)

    components = calculator.calculate(
        [SessionSlice("KETTLE", start, start + timedelta(hours=2), True)]
    )

    assert components.usage_duration_seconds == 7_200
    assert components.usage_duration_score == 17


def test_valid_day_builds_absolute_index_from_daily_sessions(
    session_factory: sessionmaker[Session],
) -> None:
    target_date = date(2026, 9, 16)
    started_at = datetime(2026, 9, 16, 0, 0, tzinfo=timezone.utc)
    with session_factory.begin() as session:
        observation = HouseholdObservationDaily(
            household_id="H001",
            observation_date=target_date,
            sample_count=4,
            expected_sample_count=4,
            coverage_ratio=Decimal("1.0000"),
            observation_status="COLLECTING",
            updated_at=started_at,
        )
        session.add(observation)
        session.flush()
        activity = HouseholdActivityDaily(
            observation_daily_id=observation.id,
            appliance_type="MICROWAVE",
            event_count=1,
            updated_at=started_at,
        )
        session.add(activity)
        session.flush()
        session.add(
            ApplianceUsageSession(
                activity_daily_id=activity.id,
                started_at=started_at,
                ended_at=started_at + timedelta(minutes=10),
                max_probability=Decimal("0.9000"),
                decision_threshold=Decimal("0.5000"),
                updated_at=started_at + timedelta(minutes=10),
            )
        )

    repository = DailyActivityIndexRepository(
        session_factory,
        "Asia/Seoul",
        expected_samples_per_day=4,
        valid_coverage_ratio=0.75,
    )
    message = repository.build_messages(target_date, ["H001"])[0]

    assert message.data_status == "VALID"
    assert message.activity_index is not None
    assert message.components is not None
    assert message.components.usage_count == 1
    assert message.components.appliance_type_count == 1
    assert message.components.usage_duration_seconds == 600

    with session_factory() as session:
        observation = session.scalar(select(HouseholdObservationDaily))
        assert observation is not None
        assert observation.observation_status == "VALID"


def test_missing_day_publishes_null_index_and_creates_sensor_gap_observation(
    session_factory: sessionmaker[Session],
) -> None:
    target_date = date(2026, 9, 16)
    repository = DailyActivityIndexRepository(
        session_factory,
        "Asia/Seoul",
        expected_samples_per_day=4,
        valid_coverage_ratio=0.75,
    )

    first = repository.build_messages(target_date, ["H001"])[0]
    second = repository.build_messages(target_date, ["H001"])[0]

    assert first.data_status == "INSUFFICIENT_DATA"
    assert first.activity_index is None
    assert first.components is None
    assert second.message_id == first.message_id

    with session_factory() as session:
        observation = session.scalar(select(HouseholdObservationDaily))
        assert observation is not None
        assert observation.sample_count == 0
        assert observation.observation_status == "SENSOR_GAP"


def test_cross_midnight_session_contributes_duration_without_new_use_count(
    session_factory: sessionmaker[Session],
) -> None:
    target_date = date(2026, 9, 16)
    previous_date = date(2026, 9, 15)
    # Asia/Seoul 2026-09-15 23:50 through 2026-09-16 00:20.
    started_at = datetime(2026, 9, 15, 14, 50, tzinfo=timezone.utc)
    ended_at = datetime(2026, 9, 15, 15, 20, tzinfo=timezone.utc)
    with session_factory.begin() as session:
        previous_observation = HouseholdObservationDaily(
            household_id="H001",
            observation_date=previous_date,
            sample_count=4,
            expected_sample_count=4,
            coverage_ratio=Decimal("1.0000"),
            observation_status="VALID",
            updated_at=started_at,
        )
        target_observation = HouseholdObservationDaily(
            household_id="H001",
            observation_date=target_date,
            sample_count=4,
            expected_sample_count=4,
            coverage_ratio=Decimal("1.0000"),
            observation_status="COLLECTING",
            updated_at=ended_at,
        )
        session.add_all([previous_observation, target_observation])
        session.flush()
        activity = HouseholdActivityDaily(
            observation_daily_id=previous_observation.id,
            appliance_type="MICROWAVE",
            event_count=1,
            updated_at=started_at,
        )
        session.add(activity)
        session.flush()
        session.add(
            ApplianceUsageSession(
                activity_daily_id=activity.id,
                started_at=started_at,
                ended_at=ended_at,
                max_probability=Decimal("0.9000"),
                decision_threshold=Decimal("0.5000"),
                updated_at=ended_at,
            )
        )

    repository = DailyActivityIndexRepository(
        session_factory,
        "Asia/Seoul",
        expected_samples_per_day=4,
        valid_coverage_ratio=0.75,
    )
    message = repository.build_messages(target_date, ["H001"])[0]

    assert message.components is not None
    assert message.components.usage_count == 0
    assert message.components.appliance_type_count == 1
    assert message.components.usage_duration_seconds == 20 * 60


def test_scheduler_publishes_each_due_date_once(
    session_factory: sessionmaker[Session],
) -> None:
    repository = DailyActivityIndexRepository(
        session_factory,
        "Asia/Seoul",
        expected_samples_per_day=4,
        valid_coverage_ratio=0.75,
    )
    publisher = Mock()
    service = DailyActivityIndexService(repository, publisher, ["H001"])
    scheduler = DailyActivityIndexScheduler(
        service,
        "Asia/Seoul",
        publish_hour=0,
        publish_minute=10,
        poll_seconds=30,
    )
    now = datetime(2026, 9, 17, 1, 0, tzinfo=timezone.utc)

    assert scheduler.run_due(now) is True
    assert scheduler.run_due(now) is False
    assert publisher.publish.call_count == 1
    assert publisher.publish.call_args.args[0].activity_date == date(2026, 9, 16)


def test_baseline_is_updated_after_daily_activity_messages(
    session_factory: sessionmaker[Session],
) -> None:
    repository = DailyActivityIndexRepository(
        session_factory,
        "Asia/Seoul",
        expected_samples_per_day=4,
        valid_coverage_ratio=0.75,
    )
    calls: list[str] = []
    publisher = Mock()
    publisher.publish.side_effect = lambda message: calls.append("publish")
    updater = Mock()
    updater.update.side_effect = lambda activity_date: calls.append("baseline")
    service = DailyActivityIndexService(
        repository,
        publisher,
        ["H001"],
        baseline_updater=updater,
    )

    service.publish_date(date(2026, 9, 16))

    assert calls == ["publish", "baseline"]
    updater.update.assert_called_once_with(date(2026, 9, 16))
