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
    <div className="flex flex-wrap items-center justify-between gap-4">
      <h1 className="text-3xl font-extrabold">
        {view === "overview" ? "오늘의 돌봄 현황" : title}
      </h1>
      {view !== "alerts" && (
        <button
          className="flex items-center gap-2 rounded-xl bg-brand-500 px-4 py-3 font-bold text-white hover:bg-brand-600"
          onClick={onToggleRegistration}
        >
          <Icon name="users" />
          대상자 등록
        </button>
      )}
    </div>
  );
}
