"""하루치 파이프라인의 순서, 창 정렬, 실패 복구."""

from datetime import date, timedelta
import threading

from gold_profile.daily import (
    ALIGN_INPUT_MISSING,
    ALIGN_SNAPSHOT_MOVING,
    STAGE_ALIGN_WINDOW,
    STAGE_GOLD_PROFILE,
    STAGE_PUBLISH,
    STAGE_USAGE_DAILY,
    STATUS_PARTIAL,
    STATUS_SUCCEEDED,
    run_daily_pipeline,
)
from gold_profile.delivery import drain_outbox


AS_OF = date(2026, 9, 20)
WINDOW = tuple(AS_OF - timedelta(days=offset) for offset in (2, 1, 0))


class FakeStages:
    """진짜 배치 대신 호출만 기록한다.

    세션 스냅샷을 실제 동작과 같게 흉내낸다. 집계가 한 번 돌면 그 날짜는 그 시점의
    전역 토큰을 갖는다. 전날만 집계하면 그 날짜만 새 토큰을 갖는 이유가 이것이다.
    """

    def __init__(
        self,
        *,
        tokens,
        current_token="t2",
        aggregatable=None,
        publish=(1, 0),
        fails=None,
        moving=False,
    ):
        self.tokens = dict(tokens)
        self.current_token = current_token
        self.aggregatable = set(WINDOW if aggregatable is None else aggregatable)
        self.publish_result = publish
        self.fails = dict(fails or {})
        self.moving = moving
        self.calls = []

    def _maybe_fail(self, name):
        left = self.fails.get(name, 0)
        if left:
            self.fails[name] = left - 1
            raise RuntimeError(f"{name} 실패")

    def window_dates(self, as_of_date):
        return WINDOW

    def power_silver(self, day):
        self.calls.append(("power-silver", day))
        self._maybe_fail("power-silver")
        return "SUCCEEDED"

    def usage_daily(self, day):
        self.calls.append(("usage-daily", day))
        self._maybe_fail("usage-daily")
        self.tokens[day] = self.current_token
        if self.moving:
            # 집계하는 사이에 새 세션 manifest가 또 도착했다.
            self.current_token += "+"

    def session_tokens(self, dates):
        return {day: self.tokens[day] for day in dates if day in self.tokens}

    def aggregatable_dates(self, dates):
        return {day for day in dates if day in self.aggregatable}

    def gold_profile(self, as_of_date):
        self.calls.append(("gold-profile", as_of_date))
        self._maybe_fail("gold-profile")
        return "SUCCEEDED"

    def publish(self):
        self.calls.append(("publish", None))
        self._maybe_fail("publish")
        return self.publish_result


def run(stages, **kwargs):
    kwargs.setdefault("sleep", lambda _seconds: None)
    return run_daily_pipeline(stages, AS_OF, **kwargs)


def stage(report, name):
    return next(item for item in report["stages"] if item["stage"] == name)


def usage_days(stages):
    return [day for name, day in stages.calls if name == "usage-daily"]


def test_stages_run_in_order_and_a_matching_window_is_left_alone():
    stages = FakeStages(tokens={day: "t2" for day in WINDOW}, current_token="t2")

    report = run(stages)

    assert report["ok"] is True
    assert [name for name, _ in stages.calls] == [
        "power-silver", "usage-daily", "gold-profile", "publish",
    ]
    # 이미 같은 스냅샷이다. 28일을 공연히 다시 집계하지 않는다.
    assert stage(report, STAGE_ALIGN_WINDOW)["detail"]["rebuilt_dates"] == []


def test_window_dates_left_on_an_older_session_snapshot_are_rebuilt_before_gold():
    # 어제만 집계하면 어제만 새 토큰을 갖는다. Gold는 그 창을 거절한다.
    stages = FakeStages(tokens={day: "t1" for day in WINDOW}, current_token="t2")

    report = run(stages)

    assert report["ok"] is True
    assert usage_days(stages) == [AS_OF, WINDOW[0], WINDOW[1]]
    detail = stage(report, STAGE_ALIGN_WINDOW)["detail"]
    assert detail["aligned"] is True
    assert detail["rebuilt_dates"] == [WINDOW[0].isoformat(), WINDOW[1].isoformat()]
    # 정렬이 끝난 뒤에 Gold가 돈다.
    assert stages.calls.index(("gold-profile", AS_OF)) > stages.calls.index(
        ("usage-daily", WINDOW[1]))


def test_a_date_that_was_never_aggregated_counts_as_out_of_alignment():
    # 토큰이 없는 날짜를 그냥 두면 Gold가 창이 모자란 프로필을 만든다.
    stages = FakeStages(tokens={AS_OF: "t1"}, current_token="t2")

    report = run(stages)

    assert usage_days(stages) == [AS_OF, WINDOW[0], WINDOW[1]]
    assert stage(report, STAGE_ALIGN_WINDOW)["detail"]["aligned"] is True


def test_dates_without_power_silver_are_reported_instead_of_retried_forever():
    stages = FakeStages(
        tokens={day: "t1" for day in WINDOW},
        current_token="t2",
        aggregatable={AS_OF},
    )

    report = run(stages)

    # 맞추지 못했지만 멈추지도 않는다. 맞출 수 없는 창인지는 Gold가 판정한다.
    assert report["ok"] is True
    detail = stage(report, STAGE_ALIGN_WINDOW)["detail"]
    assert detail["aligned"] is False
    assert detail["reason"] == ALIGN_INPUT_MISSING
    assert detail["skipped_dates"] == [WINDOW[0].isoformat(), WINDOW[1].isoformat()]
    assert stage(report, STAGE_ALIGN_WINDOW)["status"] == STATUS_PARTIAL
    assert ("gold-profile", AS_OF) in stages.calls


def test_alignment_gives_up_after_a_bounded_number_of_passes():
    # 집계하는 족족 세션 스냅샷이 또 바뀐다. 영원히 돌지 않아야 한다.
    stages = FakeStages(
        tokens={day: "t1" for day in WINDOW}, current_token="t2", moving=True
    )

    report = run(stages, alignment_passes=2)

    detail = stage(report, STAGE_ALIGN_WINDOW)["detail"]
    assert detail["aligned"] is False
    assert detail["reason"] == ALIGN_SNAPSHOT_MOVING
    assert detail["passes"] == 2


def test_a_stage_is_retried_and_a_dead_stage_stops_the_stages_after_it():
    stages = FakeStages(
        tokens={day: "t2" for day in WINDOW}, current_token="t2",
        fails={"gold-profile": 9},
    )

    report = run(stages, attempts=2)

    assert report["ok"] is False
    assert stage(report, STAGE_GOLD_PROFILE)["attempts"] == 2
    assert "gold-profile 실패" in stage(report, STAGE_GOLD_PROFILE)["error"]
    # 프로필이 없는데 발행할 것은 없다.
    assert not any(name == "publish" for name, _ in stages.calls)


def test_a_transient_failure_is_absorbed_by_the_retry():
    stages = FakeStages(
        tokens={day: "t2" for day in WINDOW}, current_token="t2",
        fails={"usage-daily": 1},
    )

    report = run(stages, attempts=3)

    assert report["ok"] is True
    assert stage(report, STAGE_USAGE_DAILY)["attempts"] == 2
    assert stage(report, STAGE_USAGE_DAILY)["status"] == STATUS_SUCCEEDED


def test_an_undelivered_profile_does_not_fail_the_day():
    # 아웃박스 행은 남아 있고 상주 발행자가 다시 보낸다. 하루를 실패로 볼 일이 아니다.
    stages = FakeStages(
        tokens={day: "t2" for day in WINDOW}, current_token="t2", publish=(0, 2)
    )

    report = run(stages)

    assert report["ok"] is True
    assert stage(report, STAGE_PUBLISH)["status"] == STATUS_PARTIAL
    assert stage(report, STAGE_PUBLISH)["detail"] == {"published": 0, "failed": 2}


class FakePublisher:
    def __init__(self, stop, *, sweeps, raises=False):
        self.stop = stop
        self.sweeps = sweeps
        self.raises = raises
        self.calls = 0

    def publish_pending(self):
        self.calls += 1
        if self.calls >= self.sweeps:
            self.stop.set()
        if self.raises:
            raise RuntimeError("데이터베이스가 끊겼다")
        return 1, 0


def test_the_publisher_loop_keeps_draining_until_it_is_told_to_stop():
    stop = threading.Event()
    publisher = FakePublisher(stop, sweeps=3)

    assert drain_outbox(publisher, 0, stop) == 0
    assert publisher.calls == 3


def test_the_publisher_loop_survives_a_failed_sweep():
    # 쓸어 담기가 실패한 것과 프로세스가 끝나야 하는 것은 다른 일이다.
    stop = threading.Event()
    publisher = FakePublisher(stop, sweeps=2, raises=True)

    assert drain_outbox(publisher, 0, stop) == 0
    assert publisher.calls == 2
