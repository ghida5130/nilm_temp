"""Kafka input, model output, baseline, and analysis event schemas."""

from datetime import datetime, time
from enum import StrEnum
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

# --데이터 형태--

# Kafka 전력 입력
class PowerMeasurement(BaseModel):
    """Canonical fields consumed from power.raw.v1."""

    model_config = ConfigDict(
        extra="ignore",
        str_strip_whitespace=True,
        allow_inf_nan=False,
    )

    message_id: UUID
    household_id: str = Field(min_length=1, max_length=50)
    device_id: str = Field(min_length=1, max_length=50)
    measured_at: datetime

    active_power: float = Field(ge=0)
    reactive_power: float
    power_factor: float = Field(ge=-1, le=1)
    current: float = Field(ge=0)

    @field_validator("measured_at")
    @classmethod
    def measured_at_must_include_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("measured_at must include a timezone")
        return value


class AppliancePrediction(BaseModel):  # AI 모델이 직접 반환한 원본 결과 
    """One appliance ON probability returned by a Predictor."""

    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    appliance_type: str
    probability: float = Field(ge=0, le=1)


class ApplianceState(BaseModel):  # 분석 서브사 확률과 threshold를 비교해 만든 최종 판정 
    """Thresholded appliance state with the evidence used for the decision."""

    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    appliance_type: str
    probability: float = Field(ge=0, le=1)
    threshold: float = Field(ge=0, le=1)
    is_on: bool


class ApplianceTransitionType(StrEnum):
    TURNED_ON = "TURNED_ON"
    TURNED_OFF = "TURNED_OFF"


class ApplianceStateTransition(BaseModel):
    """A debounced appliance state transition ready for session processing."""

    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    household_id: str = Field(min_length=1, max_length=50)
    appliance_type: str
    transition_type: ApplianceTransitionType
    previous_is_on: bool
    current_is_on: bool
    occurred_at: datetime
    confirmed_at: datetime
    probability: float = Field(ge=0, le=1)
    threshold: float = Field(ge=0, le=1)


class RoutineBaseline(BaseModel):
    id: UUID
    household_id: str
    appliance_type: str
    baseline_type: str = "ROUTINE_MISSED"
    expected_until: time
    normal_days: int = Field(ge=0)
    window_days: int = Field(gt=0)
    reliability_weight: float = Field(default=1.0, ge=0, le=1)
    enabled: bool = True

    @model_validator(mode="after")
    def normal_days_cannot_exceed_window(self) -> "RoutineBaseline":
        if self.normal_days > self.window_days:
            raise ValueError("normal_days cannot exceed window_days")
        return self


# 이상 이벤트 형식 
class AnalysisEvent(BaseModel):
    """MVP contract published to analysis.event.v1."""

    model_config = ConfigDict(extra="forbid")

    event_id: UUID
    household_id: str = Field(min_length=1, max_length=50)
    score: int = Field(ge=0, le=100)
    occurred_at: datetime
    reason: dict[str, Any]


class DlqMessage(BaseModel):
    source_topic: str
    source_partition: int
    source_offset: int
    error_code: str
    error_message: str
    failed_at: datetime
    payload: Any
