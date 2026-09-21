import { Link } from "react-router-dom";
import type { StatusEvent } from "../../types/monitoring";
import { responseLabel } from "../../types/monitoring";
import Icon from "../common/Icon";

type StreamNoticeProps = {
  notice: StatusEvent;
  subjectName: string;
  onClose: () => void;
};

export default function StreamNotice({ notice, subjectName, onClose }: StreamNoticeProps) {
  return (
    <aside
      className="flex flex-wrap items-center gap-4 rounded-2xl border border-red-200 bg-red-50 p-4"
      role="status"
    >
      <Icon className="text-red-600" name="bell" />
      <div className="min-w-60 flex-1">
        <strong>{subjectName}님의 상태가 갱신되었습니다.</strong>
        <p className="text-sm text-red-700">
          {notice.lastDetection?.description ?? responseLabel(notice.latestAlert?.subjectResponse)}
        </p>
      </div>
      <Link
        className="rounded-lg bg-red-600 px-4 py-2 font-semibold text-white"
        to={`/staff/subjects/${notice.subjectId}`}
      >
        상세보기
      </Link>
      <button aria-label="알림 닫기" onClick={onClose}>
        <Icon name="close" />
      </button>
    </aside>
  );
}
