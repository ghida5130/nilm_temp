import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import type { Risk, Subject } from "../../types/monitoring";
import { responseLabel, riskLabels } from "../../types/monitoring";
import { formatDateTime, riskBadgeClass } from "../../utils/format";
import Icon from "../common/Icon";

type SubjectListProps = {
  subjects: Subject[];
  loading: boolean;
  dataUpdatedAt: number;
};

export default function SubjectList({ subjects, loading, dataUpdatedAt }: SubjectListProps) {
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<"ALL" | Risk>("ALL");
  const visible = useMemo(
    () =>
      subjects
        .filter(
          (subject) =>
            `${subject.name} ${subject.address} ${subject.phone}`
              .toLowerCase()
              .includes(search.trim().toLowerCase()) &&
            (filter === "ALL" || subject.riskLevel === filter),
        )
        .sort((a, b) => b.riskScore - a.riskScore),
    [subjects, search, filter],
  );

  const filters = [
    { id: "ALL" as const, label: "전체" },
    ...(["DANGER", "WARNING", "NORMAL"] as Risk[]).map((risk) => ({
      id: risk,
      label: riskLabels[risk],
    })),
  ];

  return (
    <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center gap-4 border-b border-stone-100 p-4">
        <div className="flex flex-wrap gap-2">
          {filters.map((item) => (
            <button
              className={`rounded-full px-4 py-2 text-sm font-bold ${filter === item.id ? "bg-brand-500 text-white" : "bg-stone-100 text-stone-600"}`}
              key={item.id}
              aria-pressed={filter === item.id}
              onClick={() => setFilter(item.id)}
            >
              {item.label}
            </button>
          ))}
        </div>
        <label className="ml-auto flex min-w-64 flex-1 items-center gap-2 rounded-xl bg-stone-100 px-3 md:max-w-sm">
          <Icon className="text-stone-400" name="search" />
          <input
            className="h-11 min-w-0 flex-1 bg-transparent outline-none"
            aria-label="대상자 검색"
            placeholder="이름, 주소, 전화번호 검색"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-4xl text-left">
          <thead className="bg-stone-50 text-sm text-stone-500">
            <tr>
              <th className="p-4">대상자 정보</th>
              <th>위험 단계</th>
              <th>위험 점수</th>
              <th>최근 알림 응답</th>
              <th>마지막 가전 활동</th>
              <th>상세</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-stone-100">
            {visible.map((subject) => (
              <tr className="hover:bg-stone-50" key={subject.subjectId}>
                <td className="p-4">
                  <strong>
                    {subject.name} · {subject.age}세
                  </strong>
                  <small className="block text-stone-500">{subject.address}</small>
                </td>
                <td>
                  <span
                    className={`rounded-full px-3 py-1 text-xs font-bold ring-1 ${riskBadgeClass(subject.riskLevel)}`}
                  >
                    {riskLabels[subject.riskLevel]}
                  </span>
                </td>
                <td className="font-bold">{subject.riskScore}점</td>
                <td>{responseLabel(subject.latestAlert?.subjectResponse)}</td>
                <td>
                  {formatDateTime(subject.lastActivity?.occurredAt)}
                  <small className="block text-stone-500">
                    {subject.lastActivity?.applianceType}
                  </small>
                </td>
                <td>
                  <Link
                    className="font-semibold text-brand-700"
                    to={`/staff/subjects/${subject.subjectId}`}
                  >
                    상세보기
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!visible.length && (
        <p className="py-12 text-center text-stone-500">
          {loading ? "대상자를 불러오는 중입니다." : "조건에 맞는 대상자가 없습니다."}
        </p>
      )}
      <footer className="flex justify-between border-t border-stone-100 p-4 text-sm text-stone-500">
        <span>{visible.length}명</span>
        <span>
          최근 갱신{" "}
          {dataUpdatedAt ? formatDateTime(new Date(dataUpdatedAt).toISOString()) : "기록 없음"}
        </span>
      </footer>
    </section>
  );
}
