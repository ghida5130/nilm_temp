"""Realtime risk-event detection from baselines and persisted usage sessions."""

from dataclasses import dataclass
from datetime import date, datetime, time, timedelta, timezone
from time import perf_counter
from typing import Callable, Protocol
from uuid import UUID, uuid5
from zoneinfo import ZoneInfo

from sqlalchemy import func, select
from sqlalchemy.orm import Session, sessionmaker

from realtime_analysis.metrics import AnalysisMetrics, METRICS
from realtime_analysis.models import (
    ApplianceUsageSession,
    HouseholdActivityDaily,
    HouseholdObservationDaily,
)
from realtime_analysis.policy import SqlAlchemyPolicyRepository
from realtime_analysis.schemas import AnalysisEvent, RoutineBaseline
from realtime_analysis.state_tracker import DailyActivityTracker


ANALYSIS_EVENT_NAMESPACE = UUID("73e26131-3092-4d4d-baf5-b18cb9778757")


class AnomalyDetector(Protocol):
    def detect(
        self,
        household_id: str,
        measured_at: datetime,
        baselines: list[RoutineBaseline],
    ) -> list["PendingAnomaly"]: ...

    def mark_emitted(self, anomaly: "PendingAnomaly") -> None: ...


@dataclass(frozen=True)
class PendingAnomaly:
    event: AnalysisEvent
    activity_date: date
    appliance_type: str
    baseline_type: str


class RoutineMissedDetector:
    def __init__(
        self,
        tracker: DailyActivityTracker,
        minimum_baseline_strength: int,
        timezone_name: str,
    ) -> None:
        self._tracker = tracker
        self._minimum_baseline_strength = minimum_baseline_strength
        self._timezone = ZoneInfo(timezone_name)

    def detect(
        self,
        household_id: str,
        measured_at: datetime,
        baselines: list[RoutineBaseline],
    ) -> list[PendingAnomaly]:
        local_datetime = measured_at.astimezone(self._timezone)  # Kafka 메시지 시각을 한국으로 바꿈 
        activity_date = local_datetime.date()
        pending: list[PendingAnomaly] = []

        for baseline in baselines:
            if local_datetime.time().replace(tzinfo=None) < baseline.expected_until:  # 마감 시각 전이면 검사X
                continue
            if self._tracker.was_used(
                household_id, activity_date, baseline.appliance_type
            ):
                continue   
            if self._tracker.was_emitted(
                household_id,
                activity_date,
                baseline.appliance_type,
                baseline.baseline_type,
            ):
                continue

            # 드물게 사용하는 가전은 루틴 누락 판단 대상에서 제외한다.
            baseline_strength = min(
                100,
                round(
                    (baseline.normal_days / baseline.window_days)
                    * 100
                    * baseline.reliability_weight
                ),
            )
            if baseline_strength < self._minimum_baseline_strength:
                continue

            pending.append(
                PendingAnomaly(
                    event=AnalysisEvent(
                        event_id=event_id_for(
                            household_id,
                            "ROUTINE_MISSED",
                            baseline.appliance_type,
                            activity_date.isoformat(),
                        ),
                        household_id=household_id,
                        event_type="ROUTINE_MISSED",
                        occurred_at=measured_at.astimezone(timezone.utc),
                        reason={
                            "appliance_type": baseline.appliance_type,
                            "expected_until": baseline.expected_until.strftime("%H:%M"),
                            "normal_days": baseline.normal_days,
                            "window_days": baseline.window_days,
                        },
                    ),
                    activity_date=activity_date,
                    appliance_type=baseline.appliance_type,
                    baseline_type=baseline.baseline_type,
                )
            )

        return pending

    def mark_emitted(self, anomaly: PendingAnomaly) -> None:
        self._tracker.mark_emitted(
            anomaly.event.household_id,
            anomaly.activity_date,
            anomaly.appliance_type,
            anomaly.baseline_type,
        )


@dataclass(frozen=True)
class OpenUsageSession:
    id: UUID
    appliance_type: str
    started_at: datetime


class SqlAlchemyEventDetectionRepository:
    def __init__(
        self,
        session_factory: sessionmaker[Session],
        timezone_name: str,
    ) -> None:
        self._session_factory = session_factory
        self._timezone = ZoneInfo(timezone_name)

    def has_usable_observation(
        self,
        household_id: str,
        observed_at: datetime,
    ) -> bool:
        observation_date = observed_at.astimezone(self._timezone).date()
        with self._session_factory() as session:
            observation = session.scalar(
                select(HouseholdObservationDaily).where(
                    HouseholdObservationDaily.household_id == household_id,
                    HouseholdObservationDaily.observation_date == observation_date,
                )
            )
            return bool(
                observation is not None
                and observation.sample_count > 0
                and observation.observation_status in {"COLLECTING", "VALID"}
            )

    def was_used_before(
        self,
        household_id: str,
        appliance_type: str,
        day_start: datetime,
        deadline: datetime,
    ) -> bool:
        with self._session_factory() as session:
            usage_session_id = session.scalar(
                select(ApplianceUsageSession.id)
                .join(
                    HouseholdActivityDaily,
                    HouseholdActivityDaily.id
                    == ApplianceUsageSession.activity_daily_id,
                )
                .join(
                    HouseholdObservationDaily,
                    HouseholdObservationDaily.id
                    == HouseholdActivityDaily.observation_daily_id,
                )
                .where(
                    HouseholdObservationDaily.household_id == household_id,
                    HouseholdActivityDaily.appliance_type == appliance_type,
                    ApplianceUsageSession.started_at >= day_start,
                    ApplianceUsageSession.started_at <= deadline,
                )
                .limit(1)
            )
            return usage_session_id is not None

    def last_completed_activity_at(self, household_id: str) -> datetime | None:
        with self._session_factory() as session:
            value = session.scalar(
                select(func.max(ApplianceUsageSession.ended_at))
                .join(
                    HouseholdActivityDaily,
                    HouseholdActivityDaily.id
                    == ApplianceUsageSession.activity_daily_id,
                )
                .join(
                    HouseholdObservationDaily,
                    HouseholdObservationDaily.id
                    == HouseholdActivityDaily.observation_daily_id,
                )
                .where(
                    HouseholdObservationDaily.household_id == household_id,
                    ApplianceUsageSession.ended_at.is_not(None),
                )
            )
            return self._as_utc(value) if value is not None else None

    def open_sessions(
        self,
        household_id: str,
        appliance_types: set[str] | None = None,
    ) -> list[OpenUsageSession]:
        with self._session_factory() as session:
            statement = (
                select(
                    ApplianceUsageSession.id,
                    HouseholdActivityDaily.appliance_type,
                    ApplianceUsageSession.started_at,
                )
                .join(
                    HouseholdActivityDaily,
                    HouseholdActivityDaily.id
                    == ApplianceUsageSession.activity_daily_id,
                )
                .join(
                    HouseholdObservationDaily,
                    HouseholdObservationDaily.id
                    == HouseholdActivityDaily.observation_daily_id,
                )
                .where(
                    HouseholdObservationDaily.household_id == household_id,
                    ApplianceUsageSession.ended_at.is_(None),
                )
            )
            if appliance_types is not None:
                statement = statement.where(
                    HouseholdActivityDaily.appliance_type.in_(appliance_types)
                )
            return [
                OpenUsageSession(
                    id=session_id,
                    appliance_type=appliance_type,
                    started_at=self._as_utc(started_at),
                )
                for session_id, appliance_type, started_at in session.execute(
                    statement
                ).all()
            ]

    @staticmethod
    def _as_utc(value: datetime) -> datetime:
        if value.tzinfo is None:
            return value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc)


class RealtimeAnomalyDetector:
    """Evaluates the three risk events at a throttled measurement-time cadence."""

    def __init__(
        self,
        repository: SqlAlchemyEventDetectionRepository,
        policy_repository: SqlAlchemyPolicyRepository,
        timezone_name: str,
        metrics: AnalysisMetrics = METRICS,
    ) -> None:
        self._repository = repository
        self._policies = policy_repository
        self._timezone = ZoneInfo(timezone_name)
        self._metrics = metrics
        self._last_evaluated_at: dict[str, datetime] = {}
        self._emitted_event_ids: set[UUID] = set()
        self._last_emitted_at: dict[tuple[str, str, str], datetime] = {}

    def detect(
        self,
        household_id: str,
        measured_at: datetime,
        baselines: list[RoutineBaseline],
    ) -> list[PendingAnomaly]:
        observed_at = measured_at.astimezone(timezone.utc)
        if not self._is_due(household_id, observed_at):
            return []
        self._last_evaluated_at[household_id] = observed_at
        if not self._repository.has_usable_observation(
            household_id,
            measured_at,
        ):
            return []

        routine_missed_baselines = self._unemitted_routine_missed_baselines(
            household_id,
            measured_at,
            baselines,
        )
        routine_missed = []
        # An empty configured baseline list is still a real evaluation. When
        # every configured candidate was already emitted for this local date,
        # skip the repeated algorithm and its database lookups entirely.
        if not baselines or routine_missed_baselines:
            routine_missed = self._evaluate_pattern(
                "ROUTINE_MISSED",
                lambda: self._routine_missed(
                    household_id,
                    measured_at,
                    routine_missed_baselines,
                ),
            )

        pending = [
            *routine_missed,
            *self._evaluate_pattern(
                "PROLONGED_INACTIVITY",
                lambda: self._prolonged_inactivity(household_id, observed_at),
            ),
            *self._evaluate_pattern(
                "PROLONGED_APPLIANCE_USE",
                lambda: self._prolonged_appliance_use(household_id, observed_at),
            ),
        ]
        return [
            anomaly
            for anomaly in pending
            if anomaly.event.event_id not in self._emitted_event_ids
            and not self._is_in_cooldown(anomaly)
        ]

    def _evaluate_pattern(
        self,
        pattern: str,
        operation: Callable[[], list[PendingAnomaly]],
    ) -> list[PendingAnomaly]:
        started_at = perf_counter()
        try:
            anomalies = operation()
        except Exception:
            self._metrics.record_pattern_detection(pattern, "error")
            raise
        else:
            result = "detected" if anomalies else "not_detected"
            self._metrics.record_pattern_detection(pattern, result)
            return anomalies
        finally:
            self._metrics.observe_pattern_detection(
                pattern,
                perf_counter() - started_at,
            )

    def _unemitted_routine_missed_baselines(
        self,
        household_id: str,
        measured_at: datetime,
        baselines: list[RoutineBaseline],
    ) -> list[RoutineBaseline]:
        activity_date = measured_at.astimezone(self._timezone).date()
        return [
            baseline
            for baseline in baselines
            if event_id_for(
                household_id,
                "ROUTINE_MISSED",
                baseline.appliance_type,
                activity_date.isoformat(),
            )
            not in self._emitted_event_ids
        ]

    def mark_emitted(self, anomaly: PendingAnomaly) -> None:
        self._emitted_event_ids.add(anomaly.event.event_id)
        self._last_emitted_at[self._cooldown_key(anomaly)] = (
            anomaly.event.occurred_at.astimezone(timezone.utc)
        )

    def _is_in_cooldown(self, anomaly: PendingAnomaly) -> bool:
        policy = self._policies.get(anomaly.event.event_type)
        if policy is None or policy.cooldown_hours <= 0:
            return False
        last_emitted_at = self._last_emitted_at.get(
            self._cooldown_key(anomaly)
        )
        if last_emitted_at is None:
            return False
        return anomaly.event.occurred_at.astimezone(timezone.utc) < (
            last_emitted_at + timedelta(hours=policy.cooldown_hours)
        )

    @staticmethod
    def _cooldown_key(anomaly: PendingAnomaly) -> tuple[str, str, str]:
        return (
            anomaly.event.household_id,
            anomaly.event.event_type,
            anomaly.appliance_type,
        )

    def _is_due(self, household_id: str, observed_at: datetime) -> bool:
        intervals = []
        for event_type in (
            "ROUTINE_MISSED",
            "PROLONGED_INACTIVITY",
            "PROLONGED_APPLIANCE_USE",
        ):
            policy = self._policies.get(event_type)
            if policy is not None:
                intervals.append(
                    int(policy.parameters.get("evaluation_interval_seconds", 60))
                )
        interval = min(intervals, default=60)
        previous = self._last_evaluated_at.get(household_id)
        return bool(
            previous is None
            or observed_at < previous
            or (observed_at - previous).total_seconds() >= interval
        )

    def _routine_missed(
        self,
        household_id: str,
        measured_at: datetime,
        baselines: list[RoutineBaseline],
    ) -> list[PendingAnomaly]:
        policy = self._policies.get("ROUTINE_MISSED")
        if policy is None:
            return []
        minimum_strength = int(
            policy.parameters.get("minimum_baseline_strength", 80)
        )
        local_now = measured_at.astimezone(self._timezone)
        day_start = datetime.combine(
            local_now.date(),
            time.min,
            self._timezone,
        )
        result: list[PendingAnomaly] = []
        for baseline in baselines:
            deadline = datetime.combine(
                local_now.date(),
                baseline.expected_until,
                self._timezone,
            )
            if local_now < deadline:
                continue
            strength = min(
                100,
                round(
                    baseline.normal_days
                    / baseline.window_days
                    * 100
                    * baseline.reliability_weight
                ),
            )
            if strength < minimum_strength:
                continue
            if self._repository.was_used_before(
                household_id,
                baseline.appliance_type,
                day_start.astimezone(timezone.utc),
                deadline.astimezone(timezone.utc),
            ):
                continue
            event = AnalysisEvent(
                event_id=event_id_for(
                    household_id,
                    "ROUTINE_MISSED",
                    baseline.appliance_type,
                    local_now.date().isoformat(),
                ),
                household_id=household_id,
                event_type="ROUTINE_MISSED",
                occurred_at=deadline.astimezone(timezone.utc),
                reason={
                    "appliance_type": baseline.appliance_type,
                    "expected_until": baseline.expected_until.strftime("%H:%M"),
                    "normal_days": baseline.normal_days,
                    "window_days": baseline.window_days,
                },
            )
            result.append(
                PendingAnomaly(
                    event=event,
                    activity_date=local_now.date(),
                    appliance_type=baseline.appliance_type,
                    baseline_type=baseline.baseline_type,
                )
            )
        return result

    def _prolonged_inactivity(
        self,
        household_id: str,
        observed_at: datetime,
    ) -> list[PendingAnomaly]:
        policy = self._policies.get("PROLONGED_INACTIVITY")
        if policy is None or self._repository.open_sessions(household_id):
            return []
        last_activity_at = self._repository.last_completed_activity_at(
            household_id
        )
        if last_activity_at is None:
            return []
        threshold_hours = float(policy.parameters.get("inactivity_hours", 12))
        occurred_at = last_activity_at + timedelta(hours=threshold_hours)
        if observed_at < occurred_at:
            return []
        event = AnalysisEvent(
            event_id=event_id_for(
                household_id,
                "PROLONGED_INACTIVITY",
                last_activity_at.isoformat(),
            ),
            household_id=household_id,
            event_type="PROLONGED_INACTIVITY",
            occurred_at=occurred_at,
            reason={
                "last_activity_at": last_activity_at.isoformat(),
                "threshold_hours": threshold_hours,
            },
        )
        return [
            PendingAnomaly(
                event=event,
                activity_date=observed_at.astimezone(self._timezone).date(),
                appliance_type="*",
                baseline_type="PROLONGED_INACTIVITY",
            )
        ]

    def _prolonged_appliance_use(
        self,
        household_id: str,
        observed_at: datetime,
    ) -> list[PendingAnomaly]:
        policy = self._policies.get("PROLONGED_APPLIANCE_USE")
        if policy is None:
            return []
        raw_limits = policy.parameters.get("limits_minutes", {})
        limits = {
            str(appliance_type): float(minutes)
            for appliance_type, minutes in raw_limits.items()
        }
        if not limits:
            return []
        result: list[PendingAnomaly] = []
        for usage_session in self._repository.open_sessions(
            household_id,
            set(limits),
        ):
            limit_minutes = limits[usage_session.appliance_type]
            occurred_at = usage_session.started_at + timedelta(
                minutes=limit_minutes
            )
            if observed_at < occurred_at:
                continue
            event = AnalysisEvent(
                event_id=event_id_for(
                    household_id,
                    "PROLONGED_APPLIANCE_USE",
                    str(usage_session.id),
                ),
                household_id=household_id,
                event_type="PROLONGED_APPLIANCE_USE",
                occurred_at=occurred_at,
                reason={
                    "appliance_type": usage_session.appliance_type,
                    "started_at": usage_session.started_at.isoformat(),
                    "allowed_duration_minutes": limit_minutes,
                },
            )
            result.append(
                PendingAnomaly(
                    event=event,
                    activity_date=observed_at.astimezone(self._timezone).date(),
                    appliance_type=usage_session.appliance_type,
                    baseline_type="PROLONGED_APPLIANCE_USE",
                )
            )
        return result


def event_id_for(household_id: str, event_type: str, *parts: str) -> UUID:
    identity = ":".join((household_id, event_type, *parts))
    return uuid5(ANALYSIS_EVENT_NAMESPACE, identity)
