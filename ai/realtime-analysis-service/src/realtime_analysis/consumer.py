"""Kafka consumer lifecycle, validation, DLQ handling, and manual commits."""

import json
import logging
import time
from collections.abc import Callable
from threading import Event
from typing import Any

from confluent_kafka import Consumer, KafkaError, KafkaException, Message
from pydantic import ValidationError

from realtime_analysis.config import Settings
from realtime_analysis.dlq import DlqPublisher
from realtime_analysis.pipeline_timing import pipeline_timing, stage
from realtime_analysis.schemas import PowerMeasurement

logger = logging.getLogger(__name__)
MeasurementHandler = Callable[[PowerMeasurement], None]


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

    def run(self, stop_event: Event) -> None:
        self._consumer.subscribe([self._input_topic])
        logger.info("Kafka consumer started: topic=%s", self._input_topic)
        # consumer는 계속 메시지 기다림 
        try:
            while not stop_event.is_set():
                poll_started_ns = time.perf_counter_ns()
                message = self._consumer.poll(timeout=1.0)
                poll_duration_ns = time.perf_counter_ns() - poll_started_ns
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
                with stage("offset_commit"):
                    self._commit(message)
                timer.mark("dlq")
                return

            timer.bind_measurement(measurement)
            self._handler(measurement)
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
