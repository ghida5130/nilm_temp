"""Service entry point."""

import logging
import signal
from threading import Event

from realtime_analysis.anomaly_detector import RoutineMissedDetector
from realtime_analysis.baseline import BaselineRepository
from realtime_analysis.buffer import HouseholdBuffer
from realtime_analysis.config import get_settings
from realtime_analysis.consumer import AnalysisConsumer
from realtime_analysis.dlq import DlqPublisher
from realtime_analysis.event_producer import AnalysisEventPublisher
from realtime_analysis.handler import MeasurementHandler
from realtime_analysis.model_manifest import ModelManifest
from realtime_analysis.predictor import FakePredictor
from realtime_analysis.state_tracker import DailyActivityTracker
from realtime_analysis.state_decider import ApplianceStateDecider
from realtime_analysis.state_transition import ApplianceStateTransitionDetector

logger = logging.getLogger(__name__)


def main() -> None:
    settings = get_settings()
    logging.basicConfig(
        level=getattr(logging, settings.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )

    stop_event = Event()

    def request_shutdown(signum: int, frame: object) -> None:
        logger.info("Shutdown requested: signal=%s", signum)
        stop_event.set()

    signal.signal(signal.SIGINT, request_shutdown)
    signal.signal(signal.SIGTERM, request_shutdown)

    # 이상 탐지기 생성
    tracker = DailyActivityTracker()
    manifest = ModelManifest.from_json_file(settings.model_manifest_file)
    # Fake Predictor 생성 
    detector = RoutineMissedDetector(
        tracker=tracker,
        score_threshold=settings.analysis_score_threshold,
        timezone_name=settings.analysis_timezone,
    )
    handler = MeasurementHandler(
        buffer=HouseholdBuffer(settings.model_window_size),
        predictor=FakePredictor(settings.fake_on_appliance_types),
        state_decider=ApplianceStateDecider.from_manifest(manifest),
        state_transition_detector=ApplianceStateTransitionDetector(
            on_confirmation_samples=settings.appliance_on_confirmation_samples,
            off_confirmation_samples=settings.appliance_off_confirmation_samples,
            off_threshold_margin=settings.appliance_off_threshold_margin,
        ),
        baseline_repository=BaselineRepository.from_json_file(
            settings.baseline_file  
        ),
        tracker=tracker,
        detector=detector,
        event_publisher=AnalysisEventPublisher(settings),
        timezone_name=settings.analysis_timezone,
    )
    consumer = AnalysisConsumer(
        settings=settings,
        dlq_publisher=DlqPublisher(settings),
        handler=handler,
    )
    consumer.run(stop_event)


if __name__ == "__main__":
    main()
