from datetime import date, datetime, timedelta, timezone

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from realtime_analysis.database import Base
from power_silver.commit import SilverCommitRepository
from power_silver.constants import DATASET_APPLIANCE_USAGE_DAILY, DATASET_SESSION_SLICES
from power_silver.storage import LocalLakeStorage

from gold_profile.config import GoldProfileSettings
from gold_profile.job import (
    DATASET_LOGICAL_USES, DATASET_ROUTINE_BASELINE, DATASET_STATISTICAL_PROFILE,
    run_gold_profile,
)


def _publish_input_day(spark, storage, session_factory, target_date, *, used):
    repository = SilverCommitRepository(session_factory, job_name="analysis-usage-daily")
    handle = repository.start(
        target_date, input_snapshot_id=f"power-{target_date}",
        rule_version="analysis-v1", config_version="receipts=test;sessions=snapshot-1",
    )
    run_id = str(handle.run_id)
    usage_path = f"/nilm/gold/appliance_usage_daily/usage_date={target_date}/run_id={run_id}"
    slice_path = f"/nilm/silver/appliance_session_daily_slices/usage_date={target_date}/run_id={run_id}"
    usage = spark.createDataFrame(
        [(
            target_date, "H001", "KETTLE", "USED" if used else "NOT_USED",
            used, True, 900 if used else None, 1 if used else 0, "COMPLETE", "COMPLETE",
        )],
        "usage_date date, household_id string, appliance_type string, usage_status string, "
        "is_used boolean, baseline_eligible boolean, first_use_second int, "
        "usage_start_count long, analysis_status string, delivery_status string",
    )
    if used:
        start = datetime.combine(target_date, datetime.min.time(), timezone(timedelta(hours=9))).astimezone(timezone.utc) + timedelta(minutes=15)
        slice_rows = [("session-1", 1, "H001", "KETTLE", start, start + timedelta(minutes=1), False)]
    else:
        slice_rows = []
    slices = spark.createDataFrame(
        slice_rows,
        "session_id string, session_version int, household_id string, appliance_type string, "
        "original_started_at timestamp, original_ended_at timestamp, end_imputed boolean",
    )
    usage.coalesce(1).write.mode("overwrite").parquet(storage.uri(usage_path))
    slices.coalesce(1).write.mode("overwrite").parquet(storage.uri(slice_path))
    manifest = {
        "job": "analysis-usage-daily", "run_id": run_id, "attempt": handle.attempt,
        "target_date": target_date.isoformat(), "input_snapshot_id": f"power-{target_date}",
        "rule_version": "analysis-v1", "config_version": "receipts=test;sessions=snapshot-1",
        "manifest_path": f"/input-manifest/{run_id}.json",
        "outputs": {
            DATASET_APPLIANCE_USAGE_DAILY: {"path": usage_path, "row_count": 1},
            DATASET_SESSION_SLICES: {"path": slice_path, "row_count": len(slice_rows)},
        },
    }
    repository.publish(manifest)


@pytest.mark.spark
def test_job_publishes_one_atomic_shadow_profile(spark, tmp_path):
    storage = LocalLakeStorage(tmp_path / "lake")
    engine = create_engine(f"sqlite+pysqlite:///{tmp_path / 'analysis.db'}")
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
    as_of = date(2026, 9, 19)
    _publish_input_day(spark, storage, session_factory, as_of - timedelta(days=1), used=True)
    _publish_input_day(spark, storage, session_factory, as_of, used=False)
    settings = GoldProfileSettings(
        lake_local_root=str(tmp_path / "lake"), profile_window_days=2,
        profile_minimum_sample_days=1, profile_minimum_weekday_sample_days=1,
        spark_master="local[2]",
    )

    result = run_gold_profile(
        settings, as_of, storage=storage, session_factory=session_factory, spark=spark,
        now=datetime(2026, 9, 20, 0, 30, tzinfo=timezone.utc),
    )
    assert result.status == "SUCCEEDED"
    assert result.incomplete is False
    catalog = SilverCommitRepository(session_factory, job_name="gold-profile")
    refs = [
        catalog.active_version(name, as_of)
        for name in (DATASET_ROUTINE_BASELINE, DATASET_LOGICAL_USES, DATASET_STATISTICAL_PROFILE)
    ]
    assert all(ref is not None for ref in refs)
    assert len({ref.run_id for ref in refs}) == 1
    baseline = spark.read.parquet(storage.uri(refs[0].output_path))
    overall = baseline.filter("baseline_scope = 'OVERALL'").collect()[0]
    assert overall.sample_days == 2
    assert overall.active_days == 1
    assert str(overall.daily_use_probability) == "0.5000"
    assert overall.enabled is False

    repeated = run_gold_profile(
        settings, as_of, storage=storage, session_factory=session_factory, spark=spark,
    )
    assert repeated.reused_run_id == result.run_id
