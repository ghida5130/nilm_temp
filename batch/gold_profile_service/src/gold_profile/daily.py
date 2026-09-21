"""하루치를 순서대로 돌린다: 전력 Silver -> 사용 요약 -> Gold -> 아웃박스 발행.

Silver도 Gold도 일회성 배치라, 지금까지는 사람이 네 명령을 순서대로 넣어야
원본 적재부터 모니터링 프로필까지 흘렀다. 한 단계가 실패하면 그 뒤는 조용히
돌지 않았고, 어디서 멈췄는지는 로그를 뒤져야 알 수 있었다. 여기서 그 순서와
단계별 재시도를 한 실행에 묶는다.

창 정렬이 이 파이프라인의 핵심이다. Gold는 프로필 창(기본 28일)의 모든 날짜가
**같은 세션 스냅샷에서 만들어졌을 것**을 요구한다(:mod:`gold_profile.session_snapshot`).
세션은 나중에 고쳐지거나 지워지므로, 전날 하나만 집계하면 그 날짜만 새 스냅샷을
쓰고 나머지는 옛 스냅샷에 머물러 Gold가 멈춘다. 그래서 Gold를 부르기 전에
토큰이 어긋난 날짜를 찾아 다시 집계한다.

스냅샷 토큰은 세션 슬라이스가 아니라 **일별 사용 요약**의 ``config_version``에서
읽는다. 세션이 하나도 없는 날은 슬라이스 자체가 생기지 않아, 슬라이스만 보면
"세션이 없던 날"과 "아직 다시 집계하지 않은 날"을 구분할 수 없다. 사용 요약은
집계가 돌았다면 반드시 남으므로 그 구분이 된다.

이 모듈은 Spark도 DB도 직접 알지 않는다. 단계 구현은 :class:`SparkDailyStages`가
맡고, 순서와 재시도만 여기에 둔다. 덕분에 순서 자체를 Spark 없이 시험할 수 있다.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
import logging
import time

from power_silver.analysis_job import run_analysis_daily
from power_silver.catalog import SilverCatalog
from power_silver.commit import SilverCommitRepository
from power_silver.constants import (
    DATASET_APPLIANCE_USAGE_DAILY,
    DATASET_OBSERVATION,
    DATASET_POWER_CLEAN,
    RUN_SKIPPED,
    RUN_SUCCEEDED,
)
from power_silver.job import run_daily
from power_silver.targets import load_targets

from gold_profile.input_snapshot import window_dates
from gold_profile.job import run_gold_profile
from gold_profile.session_snapshot import session_state_token


logger = logging.getLogger(__name__)

STAGE_POWER_SILVER = "power-silver"
STAGE_USAGE_DAILY = "usage-daily"
STAGE_ALIGN_WINDOW = "align-window"
STAGE_GOLD_PROFILE = "gold-profile"
STAGE_PUBLISH = "publish"

STATUS_SUCCEEDED = "SUCCEEDED"
STATUS_FAILED = "FAILED"
STATUS_SKIPPED = "SKIPPED"
#: 단계는 끝났지만 남은 일이 있다. 다음 주기가 이어받는다.
STATUS_PARTIAL = "PARTIAL"

#: 창을 맞추지 못한 이유.
ALIGN_NO_TARGET_TOKEN = "NO_TARGET_TOKEN"
ALIGN_INPUT_MISSING = "INPUT_MISSING"
ALIGN_SNAPSHOT_MOVING = "SNAPSHOT_KEPT_MOVING"


class StageFailed(RuntimeError):
    """한 단계가 정해진 횟수만큼 다시 시도하고도 끝내 실패했다."""


@dataclass
class StageReport:
    name: str
    status: str = STATUS_FAILED
    attempts: int = 0
    detail: dict = field(default_factory=dict)
    error: str | None = None

    def as_dict(self) -> dict:
        return {
            "stage": self.name,
            "status": self.status,
            "attempts": self.attempts,
            "detail": self.detail,
            "error": self.error,
        }


def run_daily_pipeline(
    stages,
    as_of_date: date,
    *,
    attempts: int = 3,
    retry_seconds: float = 30.0,
    align_window: bool = True,
    publish: bool = True,
    alignment_passes: int = 3,
    sleep=time.sleep,
) -> dict:
    """한 업무 날짜를 끝까지 흘린다.

    :param stages: 단계 구현. :class:`SparkDailyStages`를 참고한다
    :param attempts: 단계 하나를 다시 시도할 최대 횟수
    :param alignment_passes: 창 정렬을 다시 확인할 최대 횟수. 정렬하는 동안 새
        세션 manifest가 도착하면 토큰이 또 움직이므로 한 번으로는 모자랄 수 있다
    :return: 단계별 결과가 담긴 보고서. 예외를 밖으로 내지 않는다
    """

    reports: list[StageReport] = []

    def attempt(name: str, call, detail: dict | None = None):
        stage = StageReport(name, detail=dict(detail or {}))
        reports.append(stage)
        for number in range(1, attempts + 1):
            stage.attempts = number
            try:
                result = call()
            except Exception as error:
                stage.error = f"{type(error).__name__}: {error}"
                logger.warning(
                    "%s 실패 (%s/%s): %s", name, number, attempts, stage.error
                )
                if number >= attempts:
                    raise StageFailed(f"{name}: {stage.error}") from error
                sleep(retry_seconds)
                continue
            stage.status = STATUS_SUCCEEDED
            stage.error = None
            return stage, result
        raise AssertionError("unreachable")

    def power_silver():
        status = stages.power_silver(as_of_date)
        # SKIPPED는 다른 작성자가 그 날짜를 맡았다는 뜻이라 실패가 아니다.
        # WAITING_INPUT은 입력이 아직 안 왔다는 뜻이므로 다시 시도한다.
        if status not in (RUN_SUCCEEDED, RUN_SKIPPED):
            raise RuntimeError(f"status={status}")
        return status

    ok = True
    try:
        stage, status = attempt(STAGE_POWER_SILVER, power_silver)
        stage.detail["status"] = status

        attempt(STAGE_USAGE_DAILY, lambda: stages.usage_daily(as_of_date))

        if align_window:
            stage, detail = attempt(
                STAGE_ALIGN_WINDOW,
                lambda: _align_window(stages, as_of_date, alignment_passes),
            )
            stage.detail.update(detail)
            if not detail["aligned"]:
                # 멈추지 않는다. 맞출 수 없는 창인지는 Gold의 규칙이 판정한다.
                stage.status = STATUS_PARTIAL
        else:
            reports.append(StageReport(STAGE_ALIGN_WINDOW, STATUS_SKIPPED))

        stage, status = attempt(
            STAGE_GOLD_PROFILE, lambda: stages.gold_profile(as_of_date)
        )
        stage.detail["status"] = status

        if publish:
            stage, counts = attempt(STAGE_PUBLISH, stages.publish)
            published, failed = counts
            stage.detail.update({"published": published, "failed": failed})
            if failed:
                # 아웃박스 행은 남아 있다. 발행자가 다음 주기에 다시 보낸다.
                stage.status = STATUS_PARTIAL
        else:
            reports.append(StageReport(STAGE_PUBLISH, STATUS_SKIPPED))
    except StageFailed as error:
        ok = False
        logger.error("일일 파이프라인이 멈췄다: %s", error)

    return {
        "as_of_date": as_of_date.isoformat(),
        "ok": ok,
        "stages": [item.as_dict() for item in reports],
    }


def _align_window(stages, as_of_date: date, passes: int) -> dict:
    """창 안의 모든 날짜를 대상 날짜와 같은 세션 스냅샷으로 맞춘다.

    토큰이 없는 날짜(아직 집계하지 않은 날)도 어긋난 것으로 본다. 그대로 두면
    Gold가 창이 모자란 프로필을 만들고, 소비자는 그 프로필을 거절한다.
    """

    rebuilt: list[date] = []
    skipped: set[date] = set()

    for number in range(1, passes + 1):
        dates = stages.window_dates(as_of_date)
        tokens = stages.session_tokens(dates)
        target = tokens.get(as_of_date)
        if target is None:
            # 방금 집계한 날짜에 출처가 없다. 맞출 기준이 없으니 그대로 둔다.
            return _alignment(False, rebuilt, skipped, number, ALIGN_NO_TARGET_TOKEN)

        stale = [
            day for day in dates
            if day != as_of_date and day not in skipped and tokens.get(day) != target
        ]
        if not stale:
            return _alignment(True, rebuilt, skipped, number, None)

        # 전력 Silver가 없는 날짜는 여기서 고칠 수 없다. 백필의 일이다.
        ready = stages.aggregatable_dates(stale)
        skipped.update(day for day in stale if day not in ready)
        todo = [day for day in stale if day in ready]
        if not todo:
            return _alignment(False, rebuilt, skipped, number, ALIGN_INPUT_MISSING)

        logger.info("세션 스냅샷을 맞추려고 %s일을 다시 집계한다", len(todo))
        for day in todo:
            stages.usage_daily(day)
            rebuilt.append(day)

    # 다시 집계하는 동안 새 세션 manifest가 계속 도착했다. 다음 주기가 이어받는다.
    return _alignment(False, rebuilt, skipped, passes, ALIGN_SNAPSHOT_MOVING)


def _alignment(aligned, rebuilt, skipped, passes, reason) -> dict:
    return {
        "aligned": aligned,
        "reason": reason,
        "passes": passes,
        "rebuilt_dates": [day.isoformat() for day in rebuilt],
        "skipped_dates": sorted(day.isoformat() for day in skipped),
    }


class SparkDailyStages:
    """실제 배치를 부르는 단계 구현.

    Spark 세션 하나를 모든 단계가 함께 쓴다. 단계마다 새로 띄우면 28일치 재집계에서
    세션 생성 비용이 계산보다 커진다.
    """

    def __init__(self, settings, *, storage, session_factory, spark, publisher):
        self._settings = settings
        self._storage = storage
        self._sessions = session_factory
        self._spark = spark
        self._publisher = publisher
        self._catalog = SilverCatalog(session_factory)
        self._repository = SilverCommitRepository(session_factory)
        self._targets = load_targets(settings.observation_targets_file)

    def window_dates(self, as_of_date: date) -> tuple[date, ...]:
        return window_dates(as_of_date, self._settings.profile_window_days)

    def power_silver(self, day: date) -> str:
        result = run_daily(
            self._settings, day,
            storage=self._storage, targets=self._targets,
            repository=self._repository, spark=self._spark, force=False,
        )
        logger.info(
            "power-silver %s: status=%s run_id=%s wait=%s",
            day, result.status, result.run_id, result.wait_reason,
        )
        return result.status

    def usage_daily(self, day: date) -> None:
        manifest = run_analysis_daily(
            self._settings, day,
            storage=self._storage, session_factory=self._sessions, spark=self._spark,
        )
        logger.info("analysis-usage-daily %s: run_id=%s", day, manifest["run_id"])

    def session_tokens(self, dates) -> dict[date, str | None]:
        versions = self._catalog.active_versions(DATASET_APPLIANCE_USAGE_DAILY, dates)
        return {day: session_state_token(ref) for day, ref in versions.items()}

    def aggregatable_dates(self, dates) -> set[date]:
        dates = tuple(dates)
        power = self._catalog.active_versions(DATASET_POWER_CLEAN, dates)
        observation = self._catalog.active_versions(DATASET_OBSERVATION, dates)
        return {day for day in dates if day in power and day in observation}

    def gold_profile(self, as_of_date: date) -> str:
        result = run_gold_profile(
            self._settings, as_of_date,
            storage=self._storage, session_factory=self._sessions,
            spark=self._spark, force=False,
        )
        logger.info(
            "gold-profile %s: status=%s run=%s reused=%s incomplete=%s",
            as_of_date, result.status, result.run_id,
            result.reused_run_id, result.incomplete,
        )
        return result.status

    def publish(self) -> tuple[int, int]:
        if self._publisher is None:
            raise RuntimeError("publisher is not configured")
        return self._publisher.publish_pending()
