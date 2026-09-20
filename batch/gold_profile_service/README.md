# Gold profile batch

`gold-profile` reads the explicitly selected ACTIVE versions of the previous 28
`appliance_usage_daily` and `appliance_session_daily_slices` partitions. It publishes
two versioned, shadow-only Gold datasets and their versioned Silver episode input:

- `routine_baseline_history`: overall and weekday appliance baselines
- Silver `appliance_logical_uses`: complete cross-day logical-use episodes
- `household_statistical_profile`: cumulative activity, inactivity, duration and recent change metrics

Missing dates do not silently shrink the window: outputs are marked
`INPUT_INCOMPLETE`, and routine baselines are disabled. The manifest records all
selected upstream version IDs and missing dates. This job deliberately does not
update the serving DB baseline and does not generate a risk score.

```console
gold-profile run --as-of 2026-09-19
gold-profile backfill --from 2026-09-01 --to 2026-09-19
gold-profile status --as-of 2026-09-19
gold-profile dirty --as-of 2026-09-19
gold-profile dirty --from 2026-09-01 --to 2026-09-28
```

## Message contract expected by monitoring-service

This job does **not** publish to Kafka yet. The publisher and its outbox are still
outstanding work. The contract below is owned by `monitoring-service`, which already
consumes it, so a publisher added here must produce exactly these field names — they are
the output column names of `routine_baseline.py` and `statistical_profile.py`.

Topic `gold.household-profile.v1`, key `household_id`, one message per household and
profile version (`profile_version` is this job's `run_id`).

```json
{
  "schema_version": 1,
  "household_id": "house-001",
  "profile_version": "<gold run_id>",
  "as_of_date": "2026-09-19",
  "window_start_date": "2026-08-23",
  "window_end_date": "2026-09-19",
  "effective_from": "2026-09-20T00:00:00+09:00",
  "published_at": "2026-09-20T03:12:40Z",
  "input_snapshot_id": "...",
  "rule_version": "gold-profile-v1",
  "statistic_rule_version": "household-statistics-v1-nearest-rank",
  "quality_status": "READY | INPUT_INCOMPLETE",
  "routine_baselines": [
    {"appliance_type": "KETTLE", "baseline_scope": "OVERALL | WEEKDAY", "weekday": null,
     "sample_days": 26, "active_days": 22, "daily_use_probability": 0.8462,
     "reliability_weight": 1.0, "first_use_time_p50_second": 30600,
     "expected_until_second": 33000, "preferred_window_start_second": 27000,
     "preferred_window_end_second": 33000, "quality_status": "READY", "enabled": true}
  ],
  "statistics": [
    {"metric_name": "CUMULATIVE_ACTIVITY_START_COUNT | INACTIVITY_ELAPSED | LOGICAL_USE_ACTIVE_DURATION | RECENT_ACTIVITY_COUNT_DELTA",
     "appliance_type": null, "weekday_group": "ALL | WEEKDAY | WEEKEND | MON..SUN",
     "time_bucket": "09:00-09:30", "sample_count": 20, "eligible_day_count": 20,
     "p50": 1.0, "p90": 3.0, "mad": 0.5, "unit": "count", "quality_status": "READY"}
  ]
}
```

How the consumer treats it:

- Unknown fields are ignored, so new output columns are safe to add.
- A message missing `household_id`, `profile_version`, `as_of_date` or `effective_from` is
  logged and skipped. The consumer never raises, so a bad profile for one household cannot
  stop the stream for the rest.
- Re-sending the same `(household_id, profile_version)` is a no-op, so the publisher may
  retry freely. Delivery does not have to be exactly once.
- `quality_status` other than `READY`, or an empty `routine_baselines` **and**
  `statistics`, is stored as `REJECTED` and never replaces the household's active profile.
- A message whose `as_of_date` is not newer than the household's active profile is stored
  as history only. Backfills and late re-sends cannot push a household back to an older
  profile.

**`effective_from` is not trusted.** This job currently writes the batch execution time
into it (`job.py` passes `now`), which is not the moment the profile becomes valid for
evaluation. The consumer therefore uses `effective_from` only as a "not before" gate and
enforces the real rule itself: a profile is eligible at evaluation time `T` only when
`as_of_date <= (KST date of T) - 1`. A profile built from data that includes the day being
evaluated is refused regardless of `effective_from`.
