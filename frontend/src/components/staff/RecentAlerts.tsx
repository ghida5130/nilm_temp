import { Link } from "react-router-dom";
import type { Subject } from "../../types/monitoring";
import { responseLabel } from "../../types/monitoring";
import { formatDateTime } from "../../utils/format";
import Icon from "../common/Icon";

export default function RecentAlerts({ subjects }: { subjects: Subject[] }) {
  const alerts = subjects
    .filter((subject) => subject.latestAlert)
    .sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt));

  return (
    <section className="rounded-2xl border border-stone-200 bg-white p-6 shadow-sm">
      <h2 className="flex items-center gap-2 text-xl font-bold">
        <Icon name="bell" />
        대상자별 최근 알림
      </h2>
      <p className="mt-2 text-stone-500">각 대상자의 가장 최근 알림입니다.</p>
      <div className="mt-5 divide-y divide-stone-100">
        {alerts.map((subject) => (
          <article className="flex flex-wrap items-center gap-4 py-4" key={subject.subjectId}>
            <span className="grid h-10 w-10 place-items-center rounded-full bg-brand-100 font-bold text-brand-700">
              {subject.name.slice(0, 1)}
            </span>
            <div className="min-w-48 flex-1">
              <strong>
                {subject.name} · {responseLabel(subject.latestAlert?.subjectResponse)}
              </strong>
              <p className="text-sm text-stone-500">
                응답 시각 {formatDateTime(subject.latestAlert?.subjectResponse.respondedAt)}
              </p>
            </div>
            <Link
              className="font-semibold text-brand-700"
              to={`/staff/subjects/${subject.subjectId}`}
            >
              상세보기
            </Link>
          </article>
        ))}
      </div>
      {!alerts.length && (
        <p className="py-10 text-center text-stone-500">조회된 최근 알림이 없습니다.</p>
      )}
    </section>
  );
}
