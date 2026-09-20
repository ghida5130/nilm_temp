"""Command line entry point for the rolling Gold profile batch."""

from __future__ import annotations

import argparse
from datetime import date, datetime, timedelta, timezone
import json
import logging

from power_silver.catalog import SilverCatalog
from power_silver.commit import SilverCommitRepository
from power_silver.constants import DATASET_APPLIANCE_USAGE_DAILY, DATASET_SESSION_SLICES
from power_silver.spark import build_session
from power_silver.storage import create_storage

from gold_profile.config import get_settings
from gold_profile.input_snapshot import build_profile_snapshot, window_dates
from gold_profile.job import (
    DATASET_ROUTINE_BASELINE, JOB_NAME, config_version_of, run_gold_profile,
)


def default_as_of_date(settings, now: datetime | None = None) -> date:
    now = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    return (now + timedelta(seconds=settings.business_utc_offset_seconds)).date() - timedelta(days=1)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="gold-profile", description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    run = commands.add_parser("run", help="build one as-of-date profile")
    run.add_argument("--as-of")
    run.add_argument("--force", action="store_true")
    backfill = commands.add_parser("backfill", help="build an inclusive as-of range")
    backfill.add_argument("--from", dest="start", required=True)
    backfill.add_argument("--to", dest="end", required=True)
    backfill.add_argument("--force", action="store_true")
    status = commands.add_parser("status", help="show the active shadow profile")
    status.add_argument("--as-of")
    dirty = commands.add_parser("dirty", help="check whether active inputs changed")
    dirty.add_argument("--as-of")
    dirty.add_argument("--from", dest="start")
    dirty.add_argument("--to", dest="end")
    return parser


def _session_factory(settings):
    from realtime_analysis.database import create_session_factory

    return create_session_factory(settings)


def _snapshot(settings, catalog, as_of_date):
    dates = window_dates(as_of_date, settings.profile_window_days)
    return build_profile_snapshot(
        as_of_date, settings.profile_window_days,
        catalog.active_versions(DATASET_APPLIANCE_USAGE_DAILY, dates),
        catalog.active_versions(DATASET_SESSION_SLICES, dates),
        rule_version=settings.profile_rule_version,
        statistic_rule_version=settings.profile_statistic_rule_version,
        analysis_run_id=settings.analysis_run_id,
        timezone_name=f"UTC{settings.business_utc_offset_seconds:+d}s",
    )


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    settings = get_settings()
    logging.basicConfig(level=getattr(logging, settings.log_level.upper(), logging.INFO))
    session_factory = _session_factory(settings)
    repository = SilverCommitRepository(session_factory, job_name=JOB_NAME)
    catalog = SilverCatalog(session_factory)
    target = date.fromisoformat(args.as_of) if getattr(args, "as_of", None) else default_as_of_date(settings)

    if args.command in ("status", "dirty"):
        if args.command == "dirty" and (args.start or args.end):
            if not args.start or not args.end:
                raise SystemExit("dirty range requires both --from and --to")
            range_start, range_end = date.fromisoformat(args.start), date.fromisoformat(args.end)
            if range_end < range_start:
                raise SystemExit("--to must not be earlier than --from")
            inspected = [
                range_start + timedelta(days=i)
                for i in range((range_end - range_start).days + 1)
            ]
        else:
            inspected = [target]
        items = []
        for inspected_date in inspected:
            active = repository.active_version(DATASET_ROUTINE_BASELINE, inspected_date)
            current = _snapshot(settings, catalog, inspected_date)
            items.append({
                "as_of_date": inspected_date.isoformat(),
                "active_run_id": str(active.run_id) if active else None,
                "active_snapshot_id": active.input_snapshot_id if active else None,
                "current_snapshot_id": current.snapshot_id,
                "input_incomplete": current.incomplete,
                "missing_usage_dates": [item.isoformat() for item in current.missing_usage_dates],
                "missing_slice_dates": [item.isoformat() for item in current.missing_slice_dates],
                "dirty": active is None or active.input_snapshot_id != current.snapshot_id,
            })
        payload = {
            "config_version": config_version_of(settings),
            "profiles": items,
            "dirty_as_of_dates": [item["as_of_date"] for item in items if item["dirty"]],
        }
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 1 if args.command == "dirty" and payload["dirty_as_of_dates"] else 0

    if args.command == "backfill":
        start, end = date.fromisoformat(args.start), date.fromisoformat(args.end)
        if end < start:
            raise SystemExit("--to must not be earlier than --from")
        wanted = [start + timedelta(days=i) for i in range((end - start).days + 1)]
    else:
        wanted = [target]

    storage = create_storage(settings)
    spark = build_session(settings)
    try:
        for as_of_date in wanted:
            result = run_gold_profile(
                settings, as_of_date, storage=storage,
                session_factory=session_factory, spark=spark,
                force=getattr(args, "force", False),
            )
            logging.info(
                "%s %s status=%s run=%s reused=%s incomplete=%s",
                JOB_NAME, as_of_date, result.status, result.run_id,
                result.reused_run_id, result.incomplete,
            )
    finally:
        spark.stop()
    return 0
