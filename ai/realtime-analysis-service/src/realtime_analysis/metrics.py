"""Prometheus metrics emitted by the realtime analysis pipeline."""

from __future__ import annotations

from collections.abc import Mapping
from threading import Lock

from prometheus_client import CollectorRegistry, Counter, Gauge, Histogram, Info, REGISTRY


STAGE_DURATION_BUCKETS = (
    0.0001,
    0.0005,
    0.001,
    0.005,
    0.01,
    0.025,
    0.05,
    0.1,
    0.25,
    0.5,
    1.0,
    2.5,
    5.0,
    10.0,
    30.0,
)

E2E_DURATION_BUCKETS = (
    0.1,
    0.25,
    0.5,
    1.0,
    2.5,
    5.0,
    10.0,
    30.0,
    60.0,
    120.0,
    300.0,
)


class AnalysisMetrics:
    """Owns the bounded-cardinality metrics exposed by ``/metrics``."""

    def __init__(self, registry: CollectorRegistry = REGISTRY) -> None:
        self.stage_duration = Histogram(
            "nilm_analysis_stage_duration_seconds",
            "Realtime analysis pipeline stage duration in seconds.",
            ("stage",),
            buckets=STAGE_DURATION_BUCKETS,
            registry=registry,
        )
        self.e2e_duration = Histogram(
            "nilm_analysis_e2e_duration_seconds",
            "Duration from measurement observation to snapshot publication.",
            buckets=E2E_DURATION_BUCKETS,
            registry=registry,
        )
        self.messages = Counter(
            "nilm_analysis_messages_total",
            "Kafka input messages by terminal processing status.",
            ("status",),
            registry=registry,
        )
        self.dlq_messages = Counter(
            "nilm_analysis_dlq_messages_total",
            "Messages successfully published to the analysis DLQ.",
            ("reason",),
            registry=registry,
        )
        self.errors = Counter(
            "nilm_analysis_errors_total",
            "Realtime analysis errors by stage and exception type.",
            ("stage", "error_type"),
            registry=registry,
        )
        self.consumer_lag = Gauge(
            "nilm_analysis_consumer_lag_messages",
            "Difference between the Kafka high watermark and consumer position.",
            ("topic", "partition"),
            registry=registry,
        )
        self.model = Info(
            "nilm_analysis_model",
            "Currently loaded analysis model.",
            registry=registry,
        )
        self._lag_labels: set[tuple[str, str]] = set()
        self._lag_lock = Lock()

    def observe_stage(self, stage: str, duration_ns: int) -> None:
        self.stage_duration.labels(stage=stage).observe(duration_ns / 1_000_000_000)

    def observe_e2e(self, duration_seconds: float) -> None:
        if duration_seconds < 0:
            self.record_error("e2e", "ClockSkew")
            return
        self.e2e_duration.observe(duration_seconds)

    def record_message(self, status: str) -> None:
        self.messages.labels(status=status).inc()

    def record_dlq(self, reason: str) -> None:
        self.dlq_messages.labels(reason=reason).inc()

    def record_error(self, stage: str, error_type: str) -> None:
        self.errors.labels(stage=stage, error_type=error_type).inc()

    def replace_consumer_lag(
        self,
        lags: Mapping[tuple[str, int], int],
    ) -> None:
        current_labels = {(topic, str(partition)) for topic, partition in lags}
        with self._lag_lock:
            for stale_topic, stale_partition in self._lag_labels - current_labels:
                self.consumer_lag.remove(stale_topic, stale_partition)
            for (topic, partition), lag in lags.items():
                self.consumer_lag.labels(
                    topic=topic,
                    partition=str(partition),
                ).set(max(lag, 0))
            self._lag_labels = current_labels

    def set_model_info(self, name: str, version: str) -> None:
        self.model.info({"name": name, "version": version})


METRICS = AnalysisMetrics()
