import type { MyDashboard } from "../../types/monitoring";
import { formatShortDateTime } from "../../utils/format";
import Icon from "../common/Icon";

export type AwayDraft = {
  duration: number;
  start: string;
};

type AwaySettingsProps = {
  draft: AwayDraft;
  mode?: MyDashboard["awayMode"];
  busy: boolean;
  dataAvailable: boolean;
  onDraftChange: (draft: AwayDraft) => void;
  onUpdateAway: (enabled: boolean) => void;
};

const primaryButton = "user-action user-action-primary";
const secondaryButton = "user-action user-action-secondary";

export default function AwaySettings({
  draft,
  mode,
  busy,
  dataAvailable,
  onDraftChange,
  onUpdateAway,
}: AwaySettingsProps) {
  const hasAwayMode = Boolean(mode?.enabled || mode?.scheduled);

  return (
    <section className="user-card" aria-labelledby="away-title">
      <h2 className="text-2xl font-bold" id="away-title">
        외출할 시간을 선택해 주세요
      </h2>
      <fieldset className="mt-5" disabled={busy}>
        <legend className="text-stone-600">외출 시간 선택</legend>
        <div className="mt-3 grid grid-cols-2 gap-3">
          {[30, 60, 120, 240].map((minutes) => (
            <button
              type="button"
              className={draft.duration === minutes ? primaryButton : secondaryButton}
              key={minutes}
              aria-pressed={draft.duration === minutes}
              onClick={() => onDraftChange({ ...draft, duration: minutes })}
            >
              {draft.duration === minutes && <Icon name="check" />}
              {minutes < 60 ? `${minutes}분` : `${minutes / 60}시간`}
            </button>
          ))}
        </div>
      </fieldset>
      <p className="mt-6 text-lg font-bold">
        {draft.start ? `${formatShortDateTime(draft.start)} 출발` : "지금 출발"} ·{" "}
        {draft.duration >= 60 && draft.duration % 60 === 0
          ? `${draft.duration / 60}시간`
          : `${draft.duration}분`} 외출
      </p>
      <div className="mt-5 grid gap-3">
        <button
          className={primaryButton}
          disabled={busy || !dataAvailable}
          onClick={() => onUpdateAway(true)}
        >
          <Icon name="walk" />
          {busy
            ? "저장 중…"
            : draft.start
              ? "선택한 시간으로 외출 예약하기"
              : "지금 외출 시작하기"}
        </button>
        {hasAwayMode && (
          <button
            className={secondaryButton}
            disabled={busy}
            onClick={() => onUpdateAway(false)}
          >
            {mode?.scheduled ? "기존 예약 취소" : "집에 돌아왔어요"}
          </button>
        )}
      </div>
      <p className="mt-4 text-stone-600">정한 시간이 지나면 외출 설정이 자동으로 해제돼요.</p>
      <div className="mt-6 grid gap-4 border-t border-stone-200 pt-5">
        <details className="group">
          <summary className="user-disclosure">외출 시간 직접 입력하기</summary>
          <div className="mt-4 grid gap-3">
            <label className="grid gap-2 font-semibold">
              외출 시간 (분)
              <input
                className="user-input"
                type="number"
                inputMode="numeric"
                min="1"
                max="1440"
                step="1"
                value={draft.duration || ""}
                disabled={busy}
                onChange={(event) =>
                  onDraftChange({ ...draft, duration: Number(event.target.value) })
                }
              />
            </label>
          </div>
        </details>
        <details>
          <summary className="user-disclosure">나중에 출발하도록 예약하기</summary>
          <div className="mt-4 grid gap-3">
            <label className="grid gap-2 font-semibold">
              출발할 날짜와 시간
              <input
                className="user-input"
                type="datetime-local"
                value={draft.start}
                disabled={busy}
                onChange={(event) => onDraftChange({ ...draft, start: event.target.value })}
              />
            </label>
            {draft.start && (
              <button
                className={secondaryButton}
                disabled={busy}
                onClick={() => onDraftChange({ ...draft, start: "" })}
              >
                지금 출발로 바꾸기
              </button>
            )}
          </div>
        </details>
      </div>
    </section>
  );
}
