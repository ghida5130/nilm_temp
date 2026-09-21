import { Link } from "react-router-dom";
import Brand from "../common/Brand";
import Icon from "../common/Icon";
import { staffNavigation } from "./staffNavigation";
import type { StaffView } from "./staffNavigation";

type StaffSidebarProps = {
  view: StaffView;
  subjectId?: string;
  onLogout: () => void;
};

export default function StaffSidebar({ view, subjectId, onLogout }: StaffSidebarProps) {
  return (
    <aside className="flex items-center gap-4 border-b border-stone-200 bg-white p-4 lg:fixed lg:inset-y-0 lg:w-[250px] lg:flex-col lg:items-stretch lg:border-r lg:border-b-0 lg:p-6">
      <Brand />
      <nav className="ml-auto flex gap-1 lg:mt-10 lg:ml-0 lg:grid" aria-label="담당자 메뉴">
        {staffNavigation.map((item) => {
          const selected = subjectId ? item.key === "subjects" : view === item.key;
          return (
            <Link
              className={`flex items-center gap-3 rounded-xl px-3 py-3 font-semibold transition ${selected ? "bg-brand-50 text-brand-700" : "text-stone-500 hover:bg-stone-50"}`}
              to={`/staff?view=${item.key}`}
              key={item.key}
            >
              <Icon name={item.icon} />
              <span className="hidden lg:inline">{item.label}</span>
            </Link>
          );
        })}
      </nav>
      <div className="mt-auto hidden border-t border-stone-100 pt-5 lg:block">
        <p className="font-bold">복지담당자</p>
        <p className="text-sm text-stone-500">함께 지키는 안심 일상</p>
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
