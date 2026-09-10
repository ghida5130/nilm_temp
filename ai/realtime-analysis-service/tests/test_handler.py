from datetime import datetime
from unittest.mock import Mock
from uuid import UUID

from realtime_analysis.anomaly_detector import RoutineMissedDetector
from realtime_analysis.baseline import BaselineRepository
from realtime_analysis.buffer import HouseholdBuffer
from realtime_analysis.handler import MeasurementHandler
from realtime_analysis.predictor import APPLIANCE_ORDER, FakePredictor
from realtime_analysis.schemas import (
    AnalysisEvent,
    AnalysisSnapshot,
    PowerMeasurement,
    RoutineBaseline,
)
from realtime_analysis.state_tracker import DailyActivityTracker
from realtime_analysis.state_decider import ApplianceStateDecider
from realtime_analysis.state_transition import ApplianceStateTransitionDetector


class RecordingPublisher:
    def __init__(self) -> None:
        self.events: list[AnalysisEvent] = []

    def publish(self, event: AnalysisEvent) -> None:
        self.events.append(event)


class RecordingSnapshotPublisher:
    def __init__(self) -> None:
        self.snapshots: list[AnalysisSnapshot] = []

    def publish(self, snapshot: AnalysisSnapshot) -> None:
        self.snapshots.append(snapshot)


def test_fake_predictor_uses_ai_experiment_appliance_order() -> None:
    predictions = FakePredictor(("KETTLE", "VACUUM_CLEANER")).predict(
        [(100.0, 10.0, 0.98, 0.5)]
    )

    assert APPLIANCE_ORDER == (
        "KETTLE",
        "INDUCTION",
        "IRON",
        "MICROWAVE",
        "HAIR_DRYER",
        "VACUUM_CLEANER",
    )
    assert [
        prediction.appliance_type for prediction in predictions
    ] == list(APPLIANCE_ORDER)
    assert {
        prediction.appliance_type
        for prediction in predictions
        if prediction.probability == 1.0
    } == {"KETTLE", "VACUUM_CLEANER"}


def measurement(second: int) -> PowerMeasurement:
    return PowerMeasurement(
        message_id=UUID(int=second + 1),
        household_id="H001",
        device_id="main",
        measured_at=datetime.fromisoformat(
            f"2026-09-08T09:00:{second:02d}+09:00"
        ),
        active_power=100.0,
        reactive_power=10.0,
        power_factor=0.98,
        current=0.5,
    )


def test_pipeline_publishes_event_after_buffer_is_ready() -> None:
    tracker = DailyActivityTracker()
    publisher = RecordingPublisher()
    snapshot_publisher = RecordingSnapshotPublisher()
    handler = MeasurementHandler(
        buffer=HouseholdBuffer(window_size=3),
        predictor=FakePredictor(),   # 지금은 FakePredictor 사용
        state_decider=ApplianceStateDecider(
            {appliance_type: 0.5 for appliance_type in APPLIANCE_ORDER}
        ),
        state_transition_detector=ApplianceStateTransitionDetector(3, 3, 0.05),
        activity_repository=Mock(),  # 단위 테스트에서는 실제 DB 저장을 대체
        baseline_repository=BaselineRepository(
            [
                RoutineBaseline(
                    id=UUID("226dfc19-3222-49a4-8fde-a314573aa23e"),
                    household_id="H001",
                    appliance_type="MICROWAVE",
                    expected_until="08:10",
                    normal_days=12,
                    window_days=14,
                )
            ]
        ),
        tracker=tracker,
        detector=RoutineMissedDetector(tracker, 80, "Asia/Seoul"),
        event_publisher=publisher,  # type: ignore[arg-type]
        snapshot_publisher=snapshot_publisher,  # type: ignore[arg-type]
        timezone_name="Asia/Seoul",
    )

    handler(measurement(0))
    handler(measurement(1))
    assert publisher.events == []

    handler(measurement(2))
    handler(measurement(3))

    assert len(publisher.events) == 1
    assert publisher.events[0].household_id == "H001"
    assert publisher.events[0].score == 86
    assert len(snapshot_publisher.snapshots) == 2
    assert snapshot_publisher.snapshots[0].snapshot_id == measurement(2).message_id


def test_pipeline_records_usage_only_after_confirmed_on_transition() -> None:
    tracker = DailyActivityTracker()
    snapshot_publisher = RecordingSnapshotPublisher()
    handler = MeasurementHandler(
        buffer=HouseholdBuffer(window_size=1),
        predictor=FakePredictor(("MICROWAVE",)),
        state_decider=ApplianceStateDecider(
            {appliance_type: 0.5 for appliance_type in APPLIANCE_ORDER}
        ),
        state_transition_detector=ApplianceStateTransitionDetector(3, 3, 0.05),
        activity_repository=Mock(),  # 단위 테스트에서는 실제 DB 저장을 대체
        baseline_repository=BaselineRepository([]),
        tracker=tracker,
        detector=RoutineMissedDetector(tracker, 80, "Asia/Seoul"),
        event_publisher=RecordingPublisher(),  # type: ignore[arg-type]
        snapshot_publisher=snapshot_publisher,  # type: ignore[arg-type]
        timezone_name="Asia/Seoul",
    )

    handler(measurement(0))
    handler(measurement(1))
    assert tracker.was_used("H001", measurement(1).measured_at.date(), "MICROWAVE") is False

    handler(measurement(2))

    assert tracker.was_used("H001", measurement(2).measured_at.date(), "MICROWAVE") is True
    microwave_states = [
        next(
            appliance
            for appliance in snapshot.appliances
            if appliance.appliance_type == "MICROWAVE"
        ).is_on
        for snapshot in snapshot_publisher.snapshots
    ]
    assert microwave_states == [False, False, True]
