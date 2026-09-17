"""SQLAlchemy mappings for the analysis_db MVP schema."""

from datetime import date, datetime
from decimal import Decimal
from typing import Any
from uuid import UUID, uuid4

from sqlalchemy import (
    BigInteger,
    Boolean,
    CheckConstraint,
    Date,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    JSON,
    Numeric,
    SmallInteger,
    String,
    UniqueConstraint,
    text,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from realtime_analysis.database import Base


APPLIANCE_TYPES = (
    "KETTLE",
    "INDUCTION",
    "IRON",
    "MICROWAVE",
    "HAIR_DRYER",
    "VACUUM_CLEANER",
)
APPLIANCE_TYPE_SQL = ", ".join(f"'{item}'" for item in APPLIANCE_TYPES)


class ModelArtifact(Base):
    __tablename__ = "model_artifact"
    __table_args__ = (
        UniqueConstraint("model_name", "version", name="uq_model_artifact_name_version"),
        CheckConstraint(
            "status IN ('VALIDATED', 'ACTIVE', 'RETIRED')",
            name="status",
        ),
        Index(
            "uq_model_artifact_single_active",
            "status",
            unique=True,
            postgresql_where=text("status = 'ACTIVE'"),
        ),
    )

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    model_name: Mapped[str] = mapped_column(String(100), nullable=False)
    version: Mapped[str] = mapped_column(String(50), nullable=False)
    artifact_uri: Mapped[str] = mapped_column(String(500), nullable=False)
    manifest_uri: Mapped[str] = mapped_column(String(500), nullable=False)
    status: Mapped[str] = mapped_column(String(20), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=text("now()")
    )


class RoutineBaselineModel(Base):
    __tablename__ = "routine_baseline"
    __table_args__ = (
        UniqueConstraint(
            "household_id",
            "appliance_type",
            "baseline_type",
            name="uq_routine_baseline_household_appliance_type",
        ),
        CheckConstraint(
            f"appliance_type IN ({APPLIANCE_TYPE_SQL})",
            name="appliance_type",
        ),
        CheckConstraint("sample_days >= 0", name="sample_days_nonnegative"),
        CheckConstraint("active_days >= 0", name="active_days_nonnegative"),
        CheckConstraint("active_days <= sample_days", name="active_days_lte_sample_days"),
        CheckConstraint(
            "daily_use_probability BETWEEN 0 AND 1",
            name="daily_use_probability",
        ),
        CheckConstraint(
            "reliability_weight BETWEEN 0 AND 1",
            name="reliability_weight",
        ),
    )

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    household_id: Mapped[str] = mapped_column(String(50), nullable=False)
    appliance_type: Mapped[str] = mapped_column(String(50), nullable=False)
    baseline_type: Mapped[str] = mapped_column(String(30), nullable=False)
    sample_days: Mapped[int] = mapped_column(SmallInteger, nullable=False)
    active_days: Mapped[int] = mapped_column(SmallInteger, nullable=False)
    daily_use_probability: Mapped[Decimal] = mapped_column(Numeric(5, 4), nullable=False)
    reliability_weight: Mapped[Decimal] = mapped_column(Numeric(5, 4), nullable=False)
    baseline_data: Mapped[dict[str, Any]] = mapped_column(
        JSON().with_variant(JSONB, "postgresql"),
        nullable=False,
    )
    enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("true"))
    calculated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class AnalysisPolicy(Base):
    __tablename__ = "analysis_policy"
    __table_args__ = (
        UniqueConstraint("policy_code", name="uq_analysis_policy_code"),
        CheckConstraint("cooldown_hours >= 0", name="cooldown_hours_nonnegative"),
    )

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    policy_code: Mapped[str] = mapped_column(String(50), nullable=False)
    algorithm_type: Mapped[str] = mapped_column(String(50), nullable=False)
    parameters: Mapped[dict[str, Any]] = mapped_column(JSONB, nullable=False)
    event_type: Mapped[str] = mapped_column(String(50), nullable=False)
    cooldown_hours: Mapped[int] = mapped_column(SmallInteger, nullable=False)
    enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("true"))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=text("now()")
    )


class HouseholdObservationDaily(Base):
    __tablename__ = "household_observation_daily"
    __table_args__ = (
        UniqueConstraint(
            "household_id",
            "observation_date",
            name="uq_household_observation_daily_household_date",
        ),
        CheckConstraint("sample_count >= 0", name="sample_count_nonnegative"),
        CheckConstraint(
            "expected_sample_count >= 0",
            name="expected_sample_count_nonnegative",
        ),
        CheckConstraint("coverage_ratio BETWEEN 0 AND 1", name="coverage_ratio"),
        CheckConstraint(
            "observation_status IN "
            "('COLLECTING', 'VALID', 'INSUFFICIENT_DATA', 'SENSOR_GAP', "
            "'PROCESSING_ERROR', 'EXCLUDED')",
            name="observation_status",
        ),
    )

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    household_id: Mapped[str] = mapped_column(String(50), nullable=False)
    observation_date: Mapped[date] = mapped_column(Date, nullable=False)
    sample_count: Mapped[int] = mapped_column(BigInteger, nullable=False, server_default=text("0"))
    expected_sample_count: Mapped[int] = mapped_column(BigInteger, nullable=False)
    coverage_ratio: Mapped[Decimal] = mapped_column(Numeric(5, 4), nullable=False)
    observation_status: Mapped[str] = mapped_column(String(30), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=text("now()")
    )


class HouseholdActivityDaily(Base):
    __tablename__ = "household_activity_daily"
    __table_args__ = (
        UniqueConstraint(
            "observation_daily_id",
            "appliance_type",
            name="uq_household_activity_daily_observation_appliance",
        ),
        CheckConstraint(
            f"appliance_type IN ({APPLIANCE_TYPE_SQL})",
            name="appliance_type",
        ),
        CheckConstraint("event_count >= 0", name="event_count_nonnegative"),
    )

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    observation_daily_id: Mapped[UUID] = mapped_column(
        ForeignKey("household_observation_daily.id", ondelete="CASCADE"),
        nullable=False,
    )
    appliance_type: Mapped[str] = mapped_column(String(50), nullable=False)
    event_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=text("now()")
    )


class ApplianceUsageSession(Base):
    __tablename__ = "appliance_usage_session"
    __table_args__ = (
        UniqueConstraint(
            "activity_daily_id",
            "started_at",
            name="uq_appliance_usage_session_activity_started",
        ),
        CheckConstraint(
            "ended_at IS NULL OR ended_at >= started_at",
            name="valid_time_range",
        ),
        CheckConstraint("max_probability BETWEEN 0 AND 1", name="max_probability"),
        CheckConstraint("decision_threshold BETWEEN 0 AND 1", name="decision_threshold"),
    )

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    activity_daily_id: Mapped[UUID] = mapped_column(
        ForeignKey("household_activity_daily.id", ondelete="CASCADE"),
        nullable=False,
    )
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    ended_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    max_probability: Mapped[Decimal] = mapped_column(Numeric(5, 4), nullable=False)
    decision_threshold: Mapped[Decimal] = mapped_column(Numeric(5, 4), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=text("now()")
    )
