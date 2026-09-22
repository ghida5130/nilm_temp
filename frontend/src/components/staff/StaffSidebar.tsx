import { Link } from "react-router-dom";
import Brand from "../common/Brand";
import Icon from "../common/Icon";
import { staffNavigation } from "./staffNavigation";
import type { StaffView } from "./staffNavigation";

type StaffSidebarProps = {
  view: StaffView;
  subjectId?: string;
  manager?: {
    name: string;
    email: string;
  };
  onLogout: () => void;
};

export default function StaffSidebar({ view, subjectId, manager, onLogout }: StaffSidebarProps) {
  return (
    <aside className="flex items-center gap-4 border-b border-stone-200 bg-white p-4 lg:fixed lg:inset-y-0 lg:z-20 lg:w-[250px] lg:flex-col lg:items-stretch lg:border-r lg:border-b-0 lg:p-6 lg:shadow-[8px_0_24px_rgb(41_37_36_/_7%)]">
      <Brand />
      <nav className="ml-auto flex gap-1 lg:mt-10 lg:ml-0 lg:grid" aria-label="담당자 메뉴">
        {staffNavigation.map((item) => {
          const selected = subjectId ? item.key === "subjects" : view === item.key;
          return (
            <Link
              className={`flex items-center gap-3 rounded-xl px-3 py-3 font-semibold transition-colors ${selected ? "bg-brand-500 text-white shadow-sm" : "text-stone-500 hover:bg-stone-50 hover:text-stone-800"}`}
              to={`/staff?view=${item.key}`}
              key={item.key}
              aria-current={selected ? "page" : undefined}
            >
              <Icon name={item.icon} />
              <span className="hidden lg:inline">{item.label}</span>
            </Link>
          );
        })}
      </nav>
      <div className="mt-auto hidden border-t border-stone-100 pt-5 lg:block">
        <div className="flex items-center gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-full bg-brand-50 font-bold text-brand-700">
            {manager?.name.trim().charAt(0) || "담"}
          </span>
          <div className="min-w-0">
            <p className="truncate font-bold text-stone-800">{manager?.name || "담당자"}</p>
            <p className="truncate text-sm text-stone-500">{manager?.email || "정보 확인 중"}</p>
          </div>
        </div>
        <button
          className="mt-4 text-sm font-semibold text-stone-500 hover:text-brand-700"
          onClick={onLogout}
        >
          로그아웃
        </button>
      </div>
    </aside>
  );
}
