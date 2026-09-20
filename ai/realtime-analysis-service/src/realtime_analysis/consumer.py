"""Kafka consumer lifecycle, validation, DLQ handling, and manual commits."""

import json
import logging
import time
from threading import Event
from typing import Any, Protocol

from confluent_kafka import (
    Consumer,
    KafkaError,
    KafkaException,
    Message,
    TopicPartition,
)
from pydantic import ValidationError

from realtime_analysis.config import Settings
from realtime_analysis.dlq import DlqPublisher
from realtime_analysis.metrics import METRICS
from realtime_analysis.pipeline_timing import pipeline_timing, stage
from realtime_analysis.schemas import PowerMeasurement, ProcessingSource

logger = logging.getLogger(__name__)
PartitionKey = tuple[str, int]


class MeasurementHandler(Protocol):
    def __call__(
        self,
        measurement: PowerMeasurement,
        source: ProcessingSource | None = None,
    ) -> None: ...

    def reset_household(self, household_id: str) -> None: ...


class AnalysisConsumer:
    def __init__(
        self,
        settings: Settings,
        dlq_publisher: DlqPublisher,
        handler: MeasurementHandler,
        consumer: Consumer | None = None,
    ) -> None:
        self._input_topic = settings.kafka_input_topic
        self._dlq_publisher = dlq_publisher
        self._handler = handler
        self._consumer = consumer or Consumer(settings.consumer_config())
        self._lag_refresh_seconds = settings.consumer_lag_refresh_seconds
        self._households_by_partition: dict[PartitionKey, set[str]] = {}

    def run(self, stop_event: Event) -> None:
        self._consumer.subscribe(
            [self._input_topic],
            on_assign=self._on_assign,
            on_revoke=self._on_revoke,
        )
        logger.info("Kafka consumer started: topic=%s", self._input_topic)
        next_lag_refresh = time.monotonic()
        # consumer는 계속 메시지 기다림 
        try:
            while not stop_event.is_set():
                poll_started_ns = time.perf_counter_ns()
                message = self._consumer.poll(timeout=1.0)
                poll_duration_ns = time.perf_counter_ns() - poll_started_ns
                now = time.monotonic()
                if now >= next_lag_refresh:
                    self._refresh_consumer_lag()
                    next_lag_refresh = now + self._lag_refresh_seconds
                if message is None:
                    continue
                if message.error():
                    if message.error().code() == KafkaError._PARTITION_EOF:
                        continue
                    raise KafkaException(message.error())
                self.process_message(
                    message,
                    poll_duration_ns=poll_duration_ns,
                )
        finally:
            self._consumer.close()
            logger.info("Kafka consumer stopped")

    def process_message(
        self,
        message: Message,
        *,
        poll_duration_ns: int | None = None,
    ) -> None:
        with pipeline_timing(
            kafka_topic=self._message_string(message, "topic"),
            kafka_partition=self._message_int(message, "partition"),
            kafka_offset=self._message_int(message, "offset"),
        ) as timer:
            if poll_duration_ns is not None:
                timer.record_duration("consumer_poll", poll_duration_ns)

            try:
                with stage("deserialize_validate"):
                    payload = self._decode_json(message)
                    measurement = PowerMeasurement.model_validate(payload)
            except (UnicodeDecodeError, json.JSONDecodeError) as error:
                raw_payload = (
                    message.value().decode("utf-8", errors="replace")
                    if message.value() is not None
                    else None
                )
                with stage("dlq_publish_ack"):
                    self._dlq_publisher.publish(
                        message,
                        raw_payload,
                        "INVALID_JSON",
                        str(error),
                    )
                METRICS.record_dlq("INVALID_JSON")
                with stage("offset_commit"):
                    self._commit(message)
                timer.mark("dlq")
                return
            except ValidationError as error:
                with stage("dlq_publish_ack"):
                    self._dlq_publisher.publish(
                        message,
                        payload,
                        "VALIDATION_ERROR",
                        str(error),
                    )
                METRICS.record_dlq("VALIDATION_ERROR")
                with stage("offset_commit"):
                    self._commit(message)
                timer.mark("dlq")
                return

            timer.bind_measurement(measurement)
            topic = self._message_string(message, "topic")
            partition = self._message_int(message, "partition")
            offset = self._message_int(message, "offset")
            self._remember_household(topic, partition, measurement.household_id)
            self._handler(
                measurement,
                ProcessingSource(
                    topic=topic,
                    partition=partition,
                    offset=offset,
                ),
            )
            with stage("offset_commit"):
                self._commit(message)
            timer.mark("processed")

    @staticmethod
    def _decode_json(message: Message) -> Any:
        raw_value = message.value()
        if raw_value is None:
            raise json.JSONDecodeError("Kafka message value is null", "", 0)
        return json.loads(raw_value.decode("utf-8"))

    def _commit(self, message: Message) -> None:
        self._consumer.commit(message=message, asynchronous=False)

    def _on_assign(
        self,
        _consumer: Consumer,
        partitions: list[TopicPartition],
    ) -> None:
        """Register newly assigned partitions without touching retained state."""

        for partition in partitions:
            self._households_by_partition.setdefault(
                (partition.topic, partition.partition),
                set(),
            )
        logger.info(
            "Kafka partitions assigned: partitions=%s",
            self._partition_labels(partitions),
        )

    def _on_revoke(
        self,
        _consumer: Consumer,
        partitions: list[TopicPartition],
    ) -> None:
        """Reset only households owned by partitions being revoked."""

        revoked_households: set[str] = set()
        for partition in partitions:
            revoked_households.update(
                self._households_by_partition.pop(
                    (partition.topic, partition.partition),
                    set(),
                )
            )
        for household_id in sorted(revoked_households):
            self._handler.reset_household(household_id)
        logger.info(
            "Kafka partitions revoked: partitions=%s reset_households=%s",
            self._partition_labels(partitions),
            len(revoked_households),
        )

    def _remember_household(
        self,
        topic: str | None,
        partition: int | None,
        household_id: str,
    ) -> None:
        if topic is None or partition is None:
            return
        self._households_by_partition.setdefault((topic, partition), set()).add(
            household_id
        )

    @staticmethod
    def _partition_labels(partitions: list[TopicPartition]) -> list[str]:
        return [
            f"{partition.topic}[{partition.partition}]"
            for partition in partitions
        ]

    def _refresh_consumer_lag(self) -> None:
        """Refresh lag for assigned partitions using locally cached watermarks."""

        try:
            assignments = self._consumer.assignment()
            positions = self._consumer.position(assignments) if assignments else []
            lags: dict[tuple[str, int], int] = {}
            for position in positions:
                if position.offset < 0:
                    continue
                _, high = self._consumer.get_watermark_offsets(
                    position,
                    timeout=1.0,
                    cached=True,
                )
                lags[(position.topic, position.partition)] = high - position.offset
            METRICS.replace_consumer_lag(lags)
        except Exception as error:
            METRICS.record_error("consumer_lag", type(error).__name__)
            logger.warning("Kafka consumer lag refresh failed", exc_info=True)

    @staticmethod
    def _message_string(message: Message, attribute: str) -> str | None:
        try:
            value = getattr(message, attribute)()
        except (AttributeError, TypeError):
            return None
        return value if isinstance(value, str) else None

    @staticmethod
    def _message_int(message: Message, attribute: str) -> int | None:
        try:
            value = getattr(message, attribute)()
        except (AttributeError, TypeError):
            return None
        return value if isinstance(value, int) else None
