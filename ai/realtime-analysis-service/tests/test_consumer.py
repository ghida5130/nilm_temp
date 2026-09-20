import json
from threading import Event
from unittest.mock import Mock, call

import pytest
from confluent_kafka import TopicPartition
from prometheus_client import CollectorRegistry

from realtime_analysis.config import Settings
from realtime_analysis.consumer import AnalysisConsumer
from realtime_analysis.metrics import AnalysisMetrics


def payload() -> dict[str, object]:
    return {
        "message_id": "8f3b2a19-4d6e-4c72-9b12-a1b2c3d4e5f6",
        "household_id": "H001",
        "device_id": "main",
        "measured_at": "2026-09-08T03:41:05.120Z",
        "active_power": 1789.47,
        "reactive_power": 340.01,
        "power_factor": 0.982,
        "current": 8.279,
    }


def kafka_message(value: object, *, partition: int = 0) -> Mock:
    message = Mock()
    message.value.return_value = json.dumps(value).encode("utf-8")
    message.topic.return_value = "power.raw.v1"
    message.partition.return_value = partition
    message.offset.return_value = 10
    return message


def service(
    consumer: Mock,
    dlq: Mock,
    handler: Mock,
    metrics: AnalysisMetrics | None = None,
) -> AnalysisConsumer:
    return AnalysisConsumer(
        settings=Settings(_env_file=None),
        dlq_publisher=dlq,
        handler=handler,
        consumer=consumer,
        **({"metrics": metrics} if metrics is not None else {}),
    )


def test_valid_message_is_handled_before_commit() -> None:
    consumer = Mock()
    handler = Mock()
    dlq = Mock()
    message = kafka_message(payload())

    service(consumer, dlq, handler).process_message(message)

    handler.assert_called_once()
    dlq.publish.assert_not_called()
    consumer.commit.assert_called_once_with(
        message=message,
        asynchronous=False,
    )


def test_invalid_message_goes_to_dlq_and_is_committed() -> None:
    consumer = Mock()
    handler = Mock()
    dlq = Mock()
    invalid_payload = payload()
    del invalid_payload["reactive_power"]
    message = kafka_message(invalid_payload)

    service(consumer, dlq, handler).process_message(message)

    dlq.publish.assert_called_once()
    handler.assert_not_called()
    consumer.commit.assert_called_once()


def test_handler_failure_is_not_committed() -> None:
    consumer = Mock()
    handler = Mock(side_effect=RuntimeError("temporary failure"))
    dlq = Mock()
    message = kafka_message(payload())

    with pytest.raises(RuntimeError, match="temporary failure"):
        service(consumer, dlq, handler).process_message(message)

    consumer.commit.assert_not_called()


def test_run_registers_rebalance_callbacks() -> None:
    consumer = Mock()
    stop_event = Event()
    stop_event.set()

    service(consumer, Mock(), Mock()).run(stop_event)

    consumer.subscribe.assert_called_once()
    assert consumer.subscribe.call_args.args == (["power.raw.v1"],)
    assert callable(consumer.subscribe.call_args.kwargs["on_assign"])
    assert callable(consumer.subscribe.call_args.kwargs["on_revoke"])


def test_revoke_resets_only_households_from_revoked_partitions() -> None:
    consumer = Mock()
    handler = Mock()
    registry = CollectorRegistry()
    metrics = AnalysisMetrics(registry)
    analysis_consumer = service(consumer, Mock(), handler, metrics)
    stop_event = Event()
    stop_event.set()
    analysis_consumer.run(stop_event)
    on_assign = consumer.subscribe.call_args.kwargs["on_assign"]
    on_revoke = consumer.subscribe.call_args.kwargs["on_revoke"]
    partition_zero = TopicPartition("power.raw.v1", 0)
    partition_one = TopicPartition("power.raw.v1", 1)

    on_assign(consumer, [partition_zero, partition_one])
    assert registry.get_sample_value(
        "nilm_analysis_consumer_rebalances_total",
        {"event": "assign"},
    ) == 1
    assert registry.get_sample_value(
        "nilm_analysis_consumer_assigned_partitions"
    ) == 2
    analysis_consumer.process_message(kafka_message(payload(), partition=0))
    second_payload = payload()
    second_payload["household_id"] = "H002"
    analysis_consumer.process_message(
        kafka_message(second_payload, partition=1)
    )

    on_revoke(consumer, [partition_zero])

    assert handler.reset_household.call_args_list == [call("H001")]
    assert registry.get_sample_value(
        "nilm_analysis_consumer_rebalances_total",
        {"event": "revoke"},
    ) == 1
    assert registry.get_sample_value(
        "nilm_analysis_consumer_assigned_partitions"
    ) == 1
    assert registry.get_sample_value(
        "nilm_analysis_household_state_resets_total"
    ) == 1

    on_revoke(consumer, [partition_one])

    assert handler.reset_household.call_args_list == [
        call("H001"),
        call("H002"),
    ]
    assert registry.get_sample_value(
        "nilm_analysis_consumer_assigned_partitions"
    ) == 0
    assert registry.get_sample_value(
        "nilm_analysis_household_state_resets_total"
    ) == 2
