"""Sustained active-power change-point detection."""

from dataclasses import dataclass
from datetime import datetime

from realtime_analysis.schemas import (
    PowerChange,
    PowerChangeDirection,
    PowerMeasurement,
)


@dataclass
class _PowerCandidate:
    baseline_power: float
    direction: PowerChangeDirection
    started_at: datetime
    count: int


class PowerChangeDetector:
    """Detect a step only after it persists for the configured sample count."""

    def __init__(self, min_delta_w: float, confirmation_samples: int) -> None:
        if min_delta_w <= 0:
            raise ValueError("min_delta_w must be greater than zero")
        if confirmation_samples < 1:
            raise ValueError("confirmation_samples must be at least one")

        self._min_delta_w = min_delta_w
        self._confirmation_samples = confirmation_samples
        self._reference_power: dict[str, float] = {}
        self._candidates: dict[str, _PowerCandidate] = {}
        self._last_observed_at: dict[str, datetime] = {}

    def detect(self, measurement: PowerMeasurement) -> PowerChange | None:
        household_id = measurement.household_id
        current_power = measurement.active_power
        last_observed_at = self._last_observed_at.get(household_id)
        if (
            last_observed_at is not None
            and measurement.measured_at <= last_observed_at
        ):
            return None
        self._last_observed_at[household_id] = measurement.measured_at

        reference = self._reference_power.get(household_id)
        if reference is None:
            self._reference_power[household_id] = current_power
            return None

        delta_w = current_power - reference
        direction = (
            PowerChangeDirection.RISE
            if delta_w > 0
            else PowerChangeDirection.FALL
        )
        candidate = self._candidates.get(household_id)

        if abs(delta_w) < self._min_delta_w:
            self._candidates.pop(household_id, None)
            self._reference_power[household_id] = current_power
            return None

        if candidate is None or candidate.direction != direction:
            candidate = _PowerCandidate(
                baseline_power=reference,
                direction=direction,
                started_at=measurement.measured_at,
                count=1,
            )
            self._candidates[household_id] = candidate
        else:
            candidate.count += 1

        if candidate.count < self._confirmation_samples:
            return None

        change = PowerChange(
            household_id=household_id,
            direction=candidate.direction,
            started_at=candidate.started_at,
            confirmed_at=measurement.measured_at,
            baseline_active_power=candidate.baseline_power,
            current_active_power=current_power,
            delta_w=current_power - candidate.baseline_power,
        )
        self._reference_power[household_id] = current_power
        self._candidates.pop(household_id, None)
        return change
