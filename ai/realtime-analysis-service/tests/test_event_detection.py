from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from uuid import UUID

import pytest
from prometheus_client import CollectorRegistry
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from realtime_analysis.anomaly_detector import (
    RealtimeAnomalyDetector,
    SqlAlchemyEventDetectionRepository,
)
from realtime_analysis.database import Base
from realtime_analysis.metrics import AnalysisMetrics
from realtime_analysis.models import (
    ApplianceUsageSession,
    HouseholdActivityDaily,
    HouseholdObservationDaily,
)
from realtime_analysis.policy import SqlAlchemyPolicyRepository
from realtime_analysis.schemas import AnalysisPolicyDefinition, RoutineBaseline


@pytest.fixture
def session_factory() -> sessionmaker[Session]:
    engine = create_engine("sqlite+pysqlite:///:memory:")
    for table_name in (
        "analysis_policy",
        "household_observation_daily",
        "household_activity_daily",
        "appliance_usage_session",
    ):
        Base.metadata.tables[table_name].create(engine)
    return sessionmaker(bind=engine, expire_on_commit=False)


def policies(session_factory: sessionmaker[Session]) -> SqlAlchemyPolicyRepository:
    repository = SqlAlchemyPolicyRepository(session_factory)
    repository.seed_missing(
        [
            AnalysisPolicyDefinition(
                policy_code="ROUTINE_MISSED_DEFAULT",
                algorithm_type="EXPECTED_USE_DEADLINE",
                parameters={
                    "minimum_baseline_strength": 80,
                    "evaluation_interval_seconds": 60,
                },
                event_type="ROUTINE_MISSED",
                cooldown_hours=24,
            ),
            AnalysisPolicyDefinition(
                policy_code="PROLONGED_INACTIVITY_DEFAULT",
                algorithm_type="LAST_ACTIVITY_ELAPSED",
                parameters={
                    "inactivity_hours": 12,
                    "evaluation_interval_seconds": 60,
                },
                event_type="PROLONGED_INACTIVITY",
                cooldown_hours=12,
            ),
            AnalysisPolicyDefinition(
                policy_code="PROLONGED_APPLIANCE_USE_DEFAULT",
                algorithm_type="OPEN_SESSION_DURATION",
                parameters={
                    "limits_minutes": {"INDUCTION": 120, "IRON": 60},
                    "evaluation_interval_seconds": 60,
                },
                event_type="PROLONGED_APPLIANCE_USE",
                cooldown_hours=24,
            ),
        ]
    )
    return repository


def detector(
    session_factory: sessionmaker[Session],
    metrics: AnalysisMetrics | None = None,
) -> RealtimeAnomalyDetector:
    return RealtimeAnomalyDetector(
        SqlAlchemyEventDetectionRepository(session_factory, "Asia/Seoul"),
        policies(session_factory),
        "Asia/Seoul",
        metrics or AnalysisMetrics(CollectorRegistry()),
    )


def baseline() -> RoutineBaseline:
    return RoutineBaseline(
        id=UUID("226dfc19-3222-49a4-8fde-a314573aa23e"),
        household_id="H001",
        appliance_type="MICROWAVE",
        expected_until="08:10",
        normal_days=12,
        window_days=14,
    )


def add_observation(
    session: Session,
    observation_date: date,
) -> HouseholdObservationDaily:
    observation = HouseholdObservationDaily(
        household_id="H001",
        observation_date=observation_date,
        sample_count=1,
        expected_sample_count=86_400,
        coverage_ratio=Decimal("0.0001"),
        observation_status="COLLECTING",
        updated_at=datetime.now(timezone.utc),
    )
    session.add(observation)
    session.flush()
    return observation


def add_usage(
    session: Session,
    observation: HouseholdObservationDaily,
    appliance_type: str,
    started_at: datetime,
    ended_at: datetime | None,
) -> ApplianceUsageSession:
    stored_start = started_at.astimezone(timezone.utc)
    stored_end = ended_at.astimezone(timezone.utc) if ended_at is not None else None
    activity = HouseholdActivityDaily(
        observation_daily_id=observation.id,
        appliance_type=appliance_type,
        event_count=1,
        updated_at=stored_start,
    )
    session.add(activity)
    session.flush()
    usage = ApplianceUsageSession(
        activity_daily_id=activity.id,
        started_at=stored_start,
        ended_at=stored_end,
        max_probability=Decimal("0.9000"),
        decision_threshold=Decimal("0.5000"),
        updated_at=stored_end or stored_start,
    )
    session.add(usage)
    session.flush()
    return usage


def test_routine_missed_is_restart_stable_and_emitted_once(
    session_factory: sessionmaker[Session],
) -> None:
    observed_at = datetime.fromisoformat("2026-09-17T09:00:00+09:00")
    with session_factory.begin() as session:
        add_observation(session, observed_at.date())

    first_detector = detector(session_factory)
    first = first_detector.detect("H001", observed_at, [baseline()])
    assert len(first) == 1
    assert first[0].event.event_type == "ROUTINE_MISSED"
    assert first[0].event.reason["appliance_type"] == "MICROWAVE"
    first_detector.mark_emitted(first[0])

    assert first_detector.detect(
        "H001",
        observed_at + timedelta(minutes=1),
        [baseline()],
    ) == []

    restarted = detector(session_factory)
    after_restart = restarted.detect("H001", observed_at, [baseline()])
    assert after_restart[0].event.event_id == first[0].event.event_id


def test_routine_missed_uses_persisted_session_before_deadline(
    session_factory: sessionmaker[Session],
) -> None:
    observed_at = datetime.fromisoformat("2026-09-17T09:00:00+09:00")
    used_at = datetime.fromisoformat("2026-09-17T08:00:00+09:00")
    with session_factory.begin() as session:
        observation = add_observation(session, observed_at.date())
        add_usage(
            session,
            observation,
            "MICROWAVE",
            used_at,
            used_at + timedelta(minutes=5),
        )

    assert detector(session_factory).detect(
        "H001",
        observed_at,
        [baseline()],
    ) == []


def test_prolonged_inactivity_uses_last_completed_activity(
    session_factory: sessionmaker[Session],
) -> None:
    observed_at = datetime.fromisoformat("2026-09-17T09:00:00+09:00")
    ended_at = observed_at - timedelta(hours=13)
    with session_factory.begin() as session:
        add_observation(session, observed_at.date())
        old_observation = add_observation(session, ended_at.date())
        add_usage(
            session,
            old_observation,
            "MICROWAVE",
            ended_at - timedelta(minutes=5),
            ended_at,
        )

    active_detector = detector(session_factory)
    events = active_detector.detect("H001", observed_at, [])

    assert [item.event.event_type for item in events] == [
        "PROLONGED_INACTIVITY"
    ]
    assert events[0].event.reason["threshold_hours"] == 12.0


def test_prolonged_appliance_use_is_emitted_once_per_open_session(
    session_factory: sessionmaker[Session],
) -> None:
    observed_at = datetime.fromisoformat("2026-09-17T09:00:00+09:00")
    started_at = observed_at - timedelta(hours=3)
    with session_factory.begin() as session:
        observation = add_observation(session, observed_at.date())
        add_usage(
            session,
            observation,
            "INDUCTION",
            started_at,
            None,
        )

    active_detector = detector(session_factory)
    events = active_detector.detect("H001", observed_at, [])

    assert [item.event.event_type for item in events] == [
        "PROLONGED_APPLIANCE_USE"
    ]
    assert events[0].event.reason["appliance_type"] == "INDUCTION"
    active_detector.mark_emitted(events[0])
    assert active_detector.detect(
        "H001",
        observed_at + timedelta(minutes=1),
        [],
    ) == []
    restarted = detector(session_factory).detect("H001", observed_at, [])
    assert restarted[0].event.event_id == events[0].event.event_id


def test_realtime_detector_records_each_pattern_evaluation(
    session_factory: sessionmaker[Session],
) -> None:
    observed_at = datetime.fromisoformat("2026-09-17T09:00:00+09:00")
    with session_factory.begin() as session:
        add_observation(session, observed_at.date())

    registry = CollectorRegistry()
    metrics = AnalysisMetrics(registry)

    events = detector(session_factory, metrics).detect(
        "H001",
        observed_at,
        [baseline()],
    )

    assert [item.event.event_type for item in events] == ["ROUTINE_MISSED"]
    assert registry.get_sample_value(
        "nilm_pattern_detection_total",
        {"pattern": "ROUTINE_MISSED", "result": "detected"},
    ) == 1
    for pattern in ("ROUTINE_MISSED", "PROLONGED_INACTIVITY", "PROLONGED_APPLIANCE_USE"):
        assert registry.get_sample_value(
            "nilm_pattern_detection_duration_seconds_count",
            {"pattern": pattern},
        ) == 1
