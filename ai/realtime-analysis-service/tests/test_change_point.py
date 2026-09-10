from datetime import datetime, timedelta
from uuid import UUID

from realtime_analysis.change_point import PowerChangeDetector
from realtime_analysis.schemas import PowerChangeDirection, PowerMeasurement


START = datetime.fromisoformat("2026-09-09T09:00:00+09:00")


def measurement(second: int, active_power: float) -> PowerMeasurement:
    return PowerMeasurement(
        message_id=UUID(int=second + 1),
        household_id="H001",
        device_id="main",
        measured_at=START + timedelta(seconds=second),
        active_power=active_power,
        reactive_power=0.0,
        power_factor=1.0,
        current=active_power / 220.0,
    )


def test_sustained_rise_is_confirmed_after_three_samples() -> None:
    detector = PowerChangeDetector(min_delta_w=500.0, confirmation_samples=3)

    assert detector.detect(measurement(0, 100.0)) is None
    assert detector.detect(measurement(1, 850.0)) is None
    assert detector.detect(measurement(2, 870.0)) is None
    change = detector.detect(measurement(3, 860.0))

    assert change is not None
    assert change.direction == PowerChangeDirection.RISE
    assert change.started_at == START + timedelta(seconds=1)
    assert change.confirmed_at == START + timedelta(seconds=3)
    assert change.delta_w == 760.0


def test_single_sample_spike_is_not_a_change_point() -> None:
    detector = PowerChangeDetector(min_delta_w=500.0, confirmation_samples=3)

    assert detector.detect(measurement(0, 100.0)) is None
    assert detector.detect(measurement(1, 900.0)) is None
    assert detector.detect(measurement(2, 100.0)) is None
    assert detector.detect(measurement(3, 110.0)) is None


def test_sustained_fall_is_detected_after_a_rise() -> None:
    detector = PowerChangeDetector(min_delta_w=500.0, confirmation_samples=2)

    detector.detect(measurement(0, 100.0))
    detector.detect(measurement(1, 900.0))
    assert detector.detect(measurement(2, 900.0)) is not None
    assert detector.detect(measurement(3, 100.0)) is None
    change = detector.detect(measurement(4, 90.0))

    assert change is not None
    assert change.direction == PowerChangeDirection.FALL
    assert change.delta_w == -810.0


def test_duplicate_measurement_does_not_confirm_a_change() -> None:
    detector = PowerChangeDetector(min_delta_w=500.0, confirmation_samples=2)

    detector.detect(measurement(0, 100.0))
    assert detector.detect(measurement(1, 900.0)) is None
    assert detector.detect(measurement(1, 900.0)) is None
    change = detector.detect(measurement(2, 900.0))

    assert change is not None
    assert change.confirmed_at == START + timedelta(seconds=2)
