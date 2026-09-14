"""실시간 분석 서비스의 실행 진입점."""

import logging
import signal
from threading import Event

from confluent_kafka.admin import AdminClient

from realtime_analysis.activity_repository import (
    SqlAlchemyApplianceActivityRepository,
)
from realtime_analysis.anomaly_detector import RoutineMissedDetector
from realtime_analysis.baseline import BaselineRepository
from realtime_analysis.buffer import HouseholdBuffer
from realtime_analysis.config import get_settings
from realtime_analysis.consumer import AnalysisConsumer
from realtime_analysis.database import create_session_factory
from realtime_analysis.dlq import DlqPublisher
from realtime_analysis.event_producer import AnalysisEventPublisher
from realtime_analysis.handler import MeasurementHandler
from realtime_analysis.health_server import ObservabilityServer
from realtime_analysis.metrics import METRICS
from realtime_analysis.model_manifest import ModelManifest
from realtime_analysis.predictor import FakePredictor
from realtime_analysis.preprocessing import StandardizingPredictor
from realtime_analysis.readiness import (
    ReadinessProbe,
    database_readiness_check,
    kafka_readiness_check,
)
from realtime_analysis.snapshot_publisher import AnalysisSnapshotPublisher
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
    METRICS.set_model_info(manifest.model_name, manifest.version)
    session_factory = create_session_factory(settings)
    # Fake Predictor 생성 
    detector = RoutineMissedDetector(
        tracker=tracker,
        score_threshold=settings.analysis_score_threshold,
        timezone_name=settings.analysis_timezone,
    )
    handler = MeasurementHandler(
        buffer=HouseholdBuffer(settings.model_window_size),
        # 실제 모델 Predictor로 교체해도 동일하게 Manifest의 mean/std를 적용한다.
        predictor=StandardizingPredictor(
            FakePredictor(settings.fake_on_appliance_types),
            manifest,
        ),
        state_decider=ApplianceStateDecider.from_manifest(manifest),
        state_transition_detector=ApplianceStateTransitionDetector(
            on_confirmation_samples=settings.appliance_on_confirmation_samples,
            off_confirmation_samples=settings.appliance_off_confirmation_samples,
            off_threshold_margin=settings.appliance_off_threshold_margin,
        ),
        # 환경변수의 analysis_db 접속 정보로 SessionFactory를 만들고
        # 실제 SQLAlchemy Repository를 Handler에 주입한다.
        activity_repository=SqlAlchemyApplianceActivityRepository(
            session_factory=session_factory,
            timezone_name=settings.analysis_timezone,
            expected_samples_per_day=settings.analysis_expected_samples_per_day,
            valid_coverage_ratio=(
                settings.analysis_observation_valid_coverage_ratio
            ),
        ),
        baseline_repository=BaselineRepository.from_json_file(
            settings.baseline_file  
        ),
        tracker=tracker,
        detector=detector,
        event_publisher=AnalysisEventPublisher(settings),
        snapshot_publisher=AnalysisSnapshotPublisher(settings),
        timezone_name=settings.analysis_timezone,
    )
    consumer = AnalysisConsumer(
        settings=settings,
        dlq_publisher=DlqPublisher(settings),
        handler=handler,
    )
    kafka_admin = AdminClient(
        {"bootstrap.servers": settings.kafka_bootstrap_servers}
    )
    readiness = ReadinessProbe(
        {
            "kafka": kafka_readiness_check(
                kafka_admin,
                settings.kafka_input_topic,
                settings.readiness_timeout_seconds,
            ),
            "database": database_readiness_check(session_factory),
            # The current MVP loads its runtime model from the packaged manifest.
            # The ACTIVE model loader can replace this check without changing HTTP.
            "model": lambda: manifest is not None,
        }
    )
    observability_server = ObservabilityServer(
        settings.http_host,
        settings.http_port,
        readiness,
    )
    observability_server.start()
    try:
        consumer.run(stop_event)
    finally:
        observability_server.stop()


if __name__ == "__main__":
    main()
