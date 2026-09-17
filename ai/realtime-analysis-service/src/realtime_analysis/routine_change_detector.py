"""Daily informational ROUTINE_CHANGED detection."""

from __future__ import annotations

import statistics
from collections import defaultdict
from datetime import date, datetime, time, timedelta, timezone
from typing import Protocol
from zoneinfo import ZoneInfo

from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from realtime_analysis.anomaly_detector import event_id_for
from realtime_analysis.baseline_updater import first_valid_logical_use
from realtime_analysis.models import (
    APPLIANCE_TYPES,
    ApplianceUsageSession,
    HouseholdActivityDaily,
    HouseholdObservationDaily,
)
from realtime_analysis.policy import SqlAlchemyPolicyRepository
from realtime_analysis.schemas import AnalysisEvent


class EventPublisher(Protocol):
    def publish(self, event: AnalysisEvent) -> None: ...


class RoutineChangeDetectionService:
    """Compares recent and reference first-use times once per completed day."""

    def __init__(
        self,
        session_factory: sessionmaker[Session],
        policy_repository: SqlAlchemyPolicyRepository,
        publisher: EventPublisher,
        timezone_name: str,
    ) -> None:
        self._session_factory = session_factory
        self._policies = policy_repository
        self._publisher = publisher
        self._timezone = ZoneInfo(timezone_name)
        self._active_signatures: dict[tuple[str, str], str] = {}

    def detect_and_publish(self, as_of_date: date) -> int:
        policy = self._policies.get("ROUTINE_CHANGED")
        if policy is None:
            return 0
        parameters = policy.parameters
        recent_days = int(parameters.get("recent_window_days", 7))
        reference_days = int(parameters.get("reference_window_days", 21))
        recent_minimum = int(parameters.get("recent_minimum_valid_days", 5))
        reference_minimum = int(
            parameters.get("reference_minimum_valid_days", 14)
        )
        minimum_probability = float(
            parameters.get("minimum_daily_use_probability", 0.7)
        )
        minimum_shift_seconds = (
            int(parameters.get("minimum_shift_minutes", 120)) * 60
        )

        recent_start = as_of_date - timedelta(days=recent_days - 1)
        reference_end = recent_start - timedelta(days=1)
        reference_start = reference_end - timedelta(days=reference_days - 1)

        with self._session_factory() as session:
            observations = session.scalars(
                select(HouseholdObservationDaily)
                .where(
                    HouseholdObservationDaily.observation_date
                    >= reference_start,
                    HouseholdObservationDaily.observation_date <= as_of_date,
                    HouseholdObservationDaily.observation_status == "VALID",
                )
                .order_by(
                    HouseholdObservationDaily.household_id,
                    HouseholdObservationDaily.observation_date,
                )
            ).all()
            by_household: dict[str, list[HouseholdObservationDaily]] = defaultdict(
                list
            )
            for observation in observations:
                by_household[observation.household_id].append(observation)

            published = 0
            for household_id, household_observations in by_household.items():
                recent_observations = [
                    item
                    for item in household_observations
                    if recent_start <= item.observation_date <= as_of_date
                ]
                reference_observations = [
                    item
                    for item in household_observations
                    if reference_start
                    <= item.observation_date
                    <= reference_end
                ]
                if (
                    len(recent_observations) < recent_minimum
                    or len(reference_observations) < reference_minimum
                ):
                    continue

                first_uses = self._first_use_seconds(
                    session,
                    household_observations,
                )
                for appliance_type in APPLIANCE_TYPES:
                    key = (household_id, appliance_type)
                    uses = first_uses.get(appliance_type, {})
                    recent_values = [
                        uses[item.observation_date]
                        for item in recent_observations
                        if item.observation_date in uses
                    ]
                    reference_values = [
                        uses[item.observation_date]
                        for item in reference_observations
                        if item.observation_date in uses
                    ]
                    recent_probability = len(recent_values) / len(
                        recent_observations
                    )
                    reference_probability = len(reference_values) / len(
                        reference_observations
                    )
                    if (
                        recent_probability < minimum_probability
                        or reference_probability < minimum_probability
                    ):
                        self._active_signatures.pop(key, None)
                        continue

                    recent_median = round(statistics.median(recent_values))
                    reference_median = round(statistics.median(reference_values))
                    shift_seconds = self._signed_circular_difference(
                        reference_median,
                        recent_median,
                    )
                    if abs(shift_seconds) < minimum_shift_seconds:
                        self._active_signatures.pop(key, None)
                        continue

                    direction = "LATER" if shift_seconds > 0 else "EARLIER"
                    recent_bucket = round(recent_median / 1800) * 1800
                    signature = f"{direction}:{recent_bucket}"
                    if self._active_signatures.get(key) == signature:
                        continue

                    event = AnalysisEvent(
                        event_id=event_id_for(
                            household_id,
                            "ROUTINE_CHANGED",
                            appliance_type,
                            signature,
                        ),
                        household_id=household_id,
                        event_type="ROUTINE_CHANGED",
                        occurred_at=datetime.now(timezone.utc),
                        reason={
                            "appliance_type": appliance_type,
                            "change_type": "FIRST_USE_TIME_SHIFT",
                            "direction": direction,
                            "previous_time": self._format_seconds(
                                reference_median
                            ),
                            "recent_time": self._format_seconds(recent_median),
                            "shift_minutes": round(shift_seconds / 60),
                            "recent_valid_days": len(recent_observations),
                            "reference_valid_days": len(
                                reference_observations
                            ),
                        },
                    )
                    self._publisher.publish(event)
                    self._active_signatures[key] = signature
                    published += 1
            return published

    def _first_use_seconds(
        self,
        session: Session,
        observations: list[HouseholdObservationDaily],
    ) -> dict[str, dict[date, int]]:
        observation_by_id = {item.id: item for item in observations}
        rows = session.execute(
            select(
                HouseholdActivityDaily.observation_daily_id,
                HouseholdActivityDaily.appliance_type,
                ApplianceUsageSession.started_at,
                ApplianceUsageSession.ended_at,
            )
            .join(
                ApplianceUsageSession,
                ApplianceUsageSession.activity_daily_id
                == HouseholdActivityDaily.id,
            )
            .where(
                HouseholdActivityDaily.observation_daily_id.in_(
                    list(observation_by_id)
                )
            )
        ).all()

        grouped: dict[
            tuple[str, date],
            list[tuple[datetime, datetime]],
        ] = defaultdict(list)
        for observation_id, appliance_type, started_at, ended_at in rows:
            observation_date = observation_by_id[observation_id].observation_date
            local_day_start = datetime.combine(
                observation_date,
                time.min,
                self._timezone,
            )
            day_start = local_day_start.astimezone(timezone.utc)
            day_end = (local_day_start + timedelta(days=1)).astimezone(
                timezone.utc
            )
            start = max(self._as_utc(started_at), day_start)
            end = min(
                self._as_utc(ended_at) if ended_at is not None else day_end,
                day_end,
            )
            if end > start:
                grouped[(appliance_type, observation_date)].append((start, end))

        result: dict[str, dict[date, int]] = defaultdict(dict)
        for (appliance_type, observation_date), sessions in grouped.items():
            first_use = first_valid_logical_use(appliance_type, sessions)
            if first_use is None:
                continue
            local_start = first_use.astimezone(self._timezone)
            result[appliance_type][observation_date] = (
                local_start.hour * 3600
                + local_start.minute * 60
                + local_start.second
            )
        return result

    @staticmethod
    def _signed_circular_difference(previous: int, recent: int) -> int:
        return (recent - previous + 43_200) % 86_400 - 43_200

    @staticmethod
    def _format_seconds(seconds: int) -> str:
        seconds %= 86_400
        hour, remainder = divmod(seconds, 3600)
        minute = remainder // 60
        return f"{hour:02d}:{minute:02d}"

    @staticmethod
    def _as_utc(value: datetime) -> datetime:
        if value.tzinfo is None:
            return value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc)
