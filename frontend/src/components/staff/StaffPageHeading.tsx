import Icon from "../common/Icon";
import type { StaffView } from "./staffNavigation";

type StaffPageHeadingProps = {
  view: StaffView;
  title?: string;
  onToggleRegistration: () => void;
};

export default function StaffPageHeading({
  view,
  title,
  onToggleRegistration,
}: StaffPageHeadingProps) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-3xl font-extrabold">
          {view === "overview" ? "오늘의 돌봄 현황" : title}
        </h1>
        <p className="mt-2 text-stone-500">
          작은 신호도 놓치지 않도록, 오늘의 일상을 살펴보세요.
        </p>
      </div>
      {view !== "alerts" && (
        <button
          className="flex items-center gap-2 rounded-xl bg-brand-500 px-4 py-3 font-bold text-brand-900 hover:bg-brand-400"
          onClick={onToggleRegistration}
        >
          <Icon name="users" />
          대상자 등록
        </button>
      )}
    </div>
  );
}
