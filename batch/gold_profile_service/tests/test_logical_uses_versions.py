from datetime import date, datetime, timedelta, timezone
from types import SimpleNamespace

import pytest

from gold_profile.logical_uses import build_logical_uses
from gold_profile.session_snapshot import require_one_session_snapshot


pytestmark = pytest.mark.spark


def test_only_latest_session_revision_is_used_across_dates(spark):
    start = datetime(2026, 9, 18, 15, 0, tzinfo=timezone.utc)
    slices = spark.createDataFrame([
        ("s1", 2, "H001", "KETTLE", start, start + timedelta(seconds=20), False),
        ("s1", 3, "H001", "KETTLE", start, start + timedelta(seconds=40), False),
    ], "session_id string, session_version int, household_id string, appliance_type string, "
       "original_started_at timestamp, original_ended_at timestamp, end_imputed boolean")

    rows = build_logical_uses(
        slices, run_id="run", rule_version="rule",
        business_utc_offset_seconds=9 * 3600,
    ).collect()

    assert len(rows) == 1
    assert rows[0].active_duration_us == 40_000_000
    assert [(item.session_id, item.session_version) for item in rows[0].source_session_refs] == [("s1", 3)]
    assert rows[0].quality_status == "VALID"


def test_partial_date_reprocessing_is_rejected_for_delete_or_date_move():
    days = (date(2026, 9, 18), date(2026, 9, 19))
    refs = [
        SimpleNamespace(config_version="receipts=a;sessions=before-delete"),
        SimpleNamespace(config_version="receipts=b;sessions=after-delete"),
    ]
    with pytest.raises(ValueError, match="different session snapshots"):
        require_one_session_snapshot(refs, days)


def test_all_dates_reprocessed_after_delete_or_move_are_accepted():
    days = (date(2026, 9, 18), date(2026, 9, 19))
    refs = [
        SimpleNamespace(config_version="receipts=a;sessions=after-change"),
        SimpleNamespace(config_version="receipts=b;sessions=after-change"),
    ]
    assert require_one_session_snapshot(refs, days) == "after-change"
