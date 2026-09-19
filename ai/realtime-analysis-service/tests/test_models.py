from realtime_analysis.database import Base
from realtime_analysis import models  # noqa: F401


def test_analysis_schema_contains_nine_tables() -> None:
    assert set(Base.metadata.tables) == {
        "model_artifact",
        "routine_baseline",
        "analysis_policy",
        "analysis_event_emission",
        "household_outing_state",
        "household_outing_period",
        "household_observation_daily",
        "household_activity_daily",
        "appliance_usage_session",
    }


def test_usage_session_keeps_probability_decision_evidence() -> None:
    columns = Base.metadata.tables["appliance_usage_session"].columns

    assert "appliance_type" not in columns
    assert "model_artifact_id" not in columns
    assert "max_probability" in columns
    assert "decision_threshold" in columns
    assert "created_at" not in columns
    assert "updated_at" in columns
    assert columns["ended_at"].nullable is True


def test_event_emission_schema_supports_cooldown_lookup() -> None:
    table = Base.metadata.tables["analysis_event_emission"]

    assert set(table.columns.keys()) == {
        "event_id",
        "household_id",
        "event_type",
        "appliance_type",
        "emitted_at",
    }
    assert list(table.primary_key.columns)[0].name == "event_id"
    assert table.columns["appliance_type"].nullable is True
    assert "ix_analysis_event_emission_cooldown" in {
        index.name for index in table.indexes
    }


def test_outing_schema_stores_only_latest_household_state() -> None:
    state_table = Base.metadata.tables["household_outing_state"]

    assert list(state_table.primary_key.columns)[0].name == "household_id"
    assert {
        "household_id",
        "is_outing",
        "outing_started_at",
        "last_returned_at",
        "last_event_id",
        "last_event_at",
        "updated_at",
    } == set(state_table.columns.keys())


def test_outing_period_schema_supports_interval_overlap_lookup() -> None:
    table = Base.metadata.tables["household_outing_period"]

    assert list(table.primary_key.columns)[0].name == "started_event_id"
    assert {
        "started_event_id",
        "household_id",
        "ended_event_id",
        "started_at",
        "ended_at",
        "updated_at",
    } == set(table.columns.keys())
    assert {
        "ix_household_outing_period_overlap",
        "uq_household_outing_period_open_household",
    }.issubset({index.name for index in table.indexes})
