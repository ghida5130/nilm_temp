"""버퍼링, 추론, 상태 감지, DB 저장과 이벤트 발행 순서를 조정한다."""

import logging

from zoneinfo import ZoneInfo

from realtime_analysis.activity_repository import ApplianceActivityRepository
from realtime_analysis.anomaly_detector import RoutineMissedDetector
from realtime_analysis.baseline import RoutineBaselineProvider
from realtime_analysis.buffer import HouseholdBuffer
from realtime_analysis.event_producer import AnalysisEventPublisher
from realtime_analysis.metrics import METRICS
from realtime_analysis.pipeline_timing import stage
from realtime_analysis.predictor import Predictor
from realtime_analysis.schemas import PowerMeasurement
from realtime_analysis.snapshot import create_analysis_snapshot
from realtime_analysis.snapshot_publisher import AnalysisSnapshotPublisher
from realtime_analysis.state_tracker import DailyActivityTracker
from realtime_analysis.state_decider import ApplianceStateDecider
from realtime_analysis.state_transition import ApplianceStateTransitionDetector

logger = logging.getLogger(__name__)


# 실제 처리 순서를 담당하는 역할 
class MeasurementHandler:
    def __init__(
        self,
        buffer: HouseholdBuffer,
        predictor: Predictor,
        state_decider: ApplianceStateDecider,
        state_transition_detector: ApplianceStateTransitionDetector,
        activity_repository: ApplianceActivityRepository,
        baseline_repository: RoutineBaselineProvider,
        tracker: DailyActivityTracker,
        detector: RoutineMissedDetector,
        event_publisher: AnalysisEventPublisher,
        snapshot_publisher: AnalysisSnapshotPublisher,
        timezone_name: str,
    ) -> None:
        self._buffer = buffer
        self._predictor = predictor
        self._state_decider = state_decider
        self._state_transition_detector = state_transition_detector
        self._activity_repository = activity_repository
        self._baseline_repository = baseline_repository
        self._tracker = tracker
        self._detector = detector
        self._event_publisher = event_publisher
        self._snapshot_publisher = snapshot_publisher
        self._timezone = ZoneInfo(timezone_name)

    def __call__(self, measurement: PowerMeasurement) -> None:
        # 모델 버퍼가 아직 차지 않았더라도 검증을 통과한 원본 샘플은 일일 관측에 집계한다.
        with stage("observation_db"):
            self._activity_repository.record_observation(
                measurement.household_id,
                measurement.measured_at,
            )
        with stage("buffer_append"):
            self._buffer.append(measurement)  # 입력값을 가구별 버퍼에 넣음
        if not self._buffer.is_ready(measurement.household_id): # 버퍼가 준비됐는지 확인
            return

        predictions = self._predictor.predict(
            self._buffer.get_window(measurement.household_id)
        )
        with stage("state_decision_transition"):
            states = self._state_decider.decide(predictions)
            activity_date = measurement.measured_at.astimezone(self._timezone).date()
            transitions = self._state_transition_detector.detect(
                measurement.household_id,
                measurement.measured_at,
                states,
            )
            # 단순 threshold 결과가 아니라 히스테리시스까지 적용된 확정 ON 상태를 구한다.
            active_appliance_types = {
                state.appliance_type
                for state in states
                if self._state_transition_detector.is_on(
                    measurement.household_id,
                    state.appliance_type,
                )
            }
        # 상태 변화와 ON 유지 확률을 DB에 저장한다.
        # Repository 내부에서 세션 INSERT와 event_count 증가를 한 트랜잭션으로 처리한다.
        with stage("activity_db"):
            self._activity_repository.record(
                household_id=measurement.household_id,
                observed_at=measurement.measured_at,
                states=states,
                transitions=transitions,
                active_appliance_types=active_appliance_types,
            )
        # DB 저장이 끝난 최신 측정값과 확정 가전 상태를 매 추론마다 발행한다.
        snapshot = create_analysis_snapshot(
            measurement=measurement,
            states=states,
            active_appliance_types=active_appliance_types,
        )
        with stage("snapshot_publish_ack"):
            self._snapshot_publisher.publish(snapshot)
        METRICS.observe_e2e(
            (snapshot.published_at - snapshot.observed_at).total_seconds()
        )
        with stage("state_tracking"):
            self._tracker.record_transitions(
                activity_date,
                transitions,
            )
        for transition in transitions:
            logger.info(
                "Appliance state changed: household=%s appliance=%s transition=%s "
                "probability=%.4f threshold=%.4f occurred_at=%s confirmed_at=%s",
                transition.household_id,
                transition.appliance_type,
                transition.transition_type,
                transition.probability,
                transition.threshold,
                transition.occurred_at.isoformat(),
                transition.confirmed_at.isoformat(),
            )

        # 가구 ID로 baseline을 찾음 
        with stage("anomaly_detection"):
            baselines = self._baseline_repository.find_by_household(
                measurement.household_id,
                measurement.measured_at,
            )
            anomalies = self._detector.detect(
                measurement.household_id,
                measurement.measured_at,
                baselines,
            )
        for anomaly in anomalies:
            with stage("event_publish_ack"):
                self._event_publisher.publish(anomaly.event)
            self._detector.mark_emitted(anomaly)
