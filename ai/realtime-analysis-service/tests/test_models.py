from realtime_analysis.database import Base
from realtime_analysis import models  # noqa: F401


def test_analysis_schema_contains_six_tables() -> None:
    assert set(Base.metadata.tables) == {
        "model_artifact",
        "routine_baseline",
        "analysis_policy",
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
