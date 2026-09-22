import AwaySettings from "./AwaySettings";
import type { AwayDraft } from "./AwaySettings";
import UserTabStatus from "./UserTabStatus";
import type { UserTabStatusProps } from "./UserTabStatus";

type UserAwayTabProps = {
  draft: AwayDraft;
  busy: boolean;
  dataAvailable: boolean;
  comparison: boolean;
  status: UserTabStatusProps;
  onDraftChange: (draft: AwayDraft) => void;
  onStartAway: (duration: number) => void;
  onBack: () => void;
};

export default function UserAwayTab({
  draft,
  busy,
  dataAvailable,
  comparison,
  status,
  onDraftChange,
  onStartAway,
  onBack,
}: UserAwayTabProps) {
  return (
    <>
      <div>
        <button
          className="mb-3 min-h-12 rounded-xl px-2 text-lg font-semibold text-stone-600 transition-colors hover:bg-stone-100 hover:text-stone-800"
          type="button"
          onClick={onBack}
        >
          ← 이전 화면
        </button>
        <h1 className="text-center text-3xl leading-snug font-bold">외출 시간 정하기</h1>
        {comparison && (
          <p className="mt-2 text-sm font-semibold text-brand-700">기존 디자인 비교 화면</p>
        )}
      </div>
      <UserTabStatus {...status} />
      <AwaySettings
        draft={draft}
        busy={busy}
        dataAvailable={dataAvailable}
        onDraftChange={onDraftChange}
        onStartAway={onStartAway}
      />
    </>
  );
}
