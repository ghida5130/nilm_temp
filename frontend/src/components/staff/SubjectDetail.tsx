import { useState } from "react";
import { usePowerUsageQuery, useSubjectEventsQuery } from "../../hooks/api";
import { responseLabel, riskLabels } from "../../types/monitoring";
import type { Subject } from "../../types/monitoring";
import { formatDateTime, riskBadgeClass, telephoneHref, todayInSeoul } from "../../utils/format";
import Icon from "../common/Icon";

const cardClass = "rounded-2xl border border-stone-200 bg-white p-6 shadow-sm";

function EventHistory({ subject }: { subject: Subject }) {
  const query = useSubjectEventsQuery(subject.subjectId, subject.version);
  const events = query.data?.pages.flatMap((page) => page.events) ?? [];
  return (
    <section className={cardClass}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-xl font-bold">
          <Icon name="history" />
          최근 7일 이상 징후 기록
        </h2>
        <button
          className="rounded-lg bg-stone-100 px-3 py-2 font-semibold text-stone-700"
          onClick={() => void query.refetch()}
        >
          새로고침
        </button>
      </div>
      {query.isError && (
        <p className="mt-4 rounded-xl bg-red-50 p-4 text-red-700" role="alert">
          기록을 불러오지 못했습니다.
        </p>
      )}
      <div className="mt-5 divide-y divide-stone-100">
        {events.map((event) => (
          <article className="grid gap-3 py-4 md:grid-cols-[auto_1fr]" key={event.eventId}>
            <span
              className={`h-fit rounded-full px-3 py-1 text-xs font-bold ring-1 ${riskBadgeClass(event.riskLevel)}`}
            >
              {riskLabels[event.riskLevel]} · {event.riskScore}점
            </span>
            <div>
              <strong>{event.description}</strong>
              <p className="mt-1 text-sm text-stone-500">{formatDateTime(event.occurredAt)}</p>
              <span className="text-sm text-stone-600">
                {responseLabel(event.alert?.subjectResponse)}
              </span>
            </div>
          </article>
        ))}
      </div>
      {!events.length && !query.isLoading && (
        <p className="py-8 text-center text-stone-500">최근 기록된 이상 징후가 없습니다.</p>
      )}
      {query.hasNextPage && (
        <button
          className="mt-4 rounded-xl border border-stone-300 px-4 py-2 font-semibold"
          onClick={() => void query.fetchNextPage()}
          disabled={query.isFetchingNextPage}
        >
          {query.isFetchingNextPage ? "조회 중…" : "기록 더 보기"}
        </button>
      )}
    </section>
  );
}

function PowerUsage({ subject }: { subject: Subject }) {
  const [date, setDate] = useState(todayInSeoul);
  const query = usePowerUsageQuery(subject.subjectId, date, subject.version);
  const data = query.data;
  const maximum = Math.max(1, ...(data?.hourlyUsage.map((bucket) => bucket.usage ?? 0) ?? []));
  return (
    <section className={cardClass}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-xl font-bold">
          <Icon name="device" />
          하루 전력 사용량
        </h2>
        <label className="flex items-center gap-2 text-sm font-semibold text-stone-600">
          조회일
          <input
            className="rounded-lg border border-stone-300 px-3 py-2"
            type="date"
            value={date}
            max={todayInSeoul()}
            onChange={(event) => setDate(event.target.value)}
          />
        </label>
      </div>
      {query.isError && (
        <p className="mt-4 rounded-xl bg-red-50 p-4 text-red-700" role="alert">
          전력 사용량을 불러오지 못했습니다.{" "}
          <button className="font-bold underline" onClick={() => void query.refetch()}>
            다시 시도
          </button>
        </p>
      )}
      {data ? (
        <>
          <p className="mt-6 text-3xl font-extrabold text-stone-800">
            {data.totalUsage.toLocaleString()}{" "}
            <small className="text-base text-stone-500">{data.unit}</small>
          </p>
          <div
            className="mt-6 flex h-44 items-end gap-1"
            role="img"
            aria-label={`${date} 시간별 전력 사용량`}
          >
            {data.hourlyUsage.map((bucket) => (
              <div
                className="flex h-full min-w-0 flex-1 flex-col justify-end gap-2"
                key={bucket.hour}
                title={`${bucket.hour}시: ${bucket.usage ?? "—"} ${data.unit}`}
              >
                <span
                  className={`min-h-0 rounded-t ${bucket.status === "COMPLETE" ? "bg-brand-500" : bucket.status === "PARTIAL" ? "bg-brand-200" : "bg-stone-100"}`}
                  style={{ height: `${Math.max(2, ((bucket.usage ?? 0) / maximum) * 100)}%` }}
                />
                <small className="text-center text-[10px] text-stone-400">
                  {bucket.hour % 3 === 0 ? bucket.hour : ""}
                </small>
              </div>
            ))}
          </div>
          <p className="mt-4 text-sm text-stone-500">
            마지막 집계 {formatDateTime(data.updatedAt)}
          </p>
        </>
      ) : (
        !query.isError && (
          <p className="py-8 text-center text-stone-500">전력 사용량을 불러오는 중입니다.</p>
        )
      )}
    </section>
  );
}

export default function SubjectDetail({ subject }: { subject: Subject }) {
  const scores = subject.riskTrend?.dailyScores ?? [];
  const maximum = Math.max(100, ...scores.map((point) => point.score));
  const points = scores
    .map(
      (point, index) =>
        `${30 + (index * 540) / Math.max(1, scores.length - 1)},${150 - (point.score / maximum) * 125}`,
    )
    .join(" ");
  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <span className="grid h-14 w-14 place-items-center rounded-full bg-brand-100 text-xl font-extrabold text-brand-700">
            {subject.name.slice(0, 1)}
          </span>
          <div>
            <h1 className="text-3xl font-extrabold">
              {subject.name}
              <span className="text-lg font-medium">님</span>
            </h1>
            <p className="text-stone-500">
              {subject.age}세 · {subject.address}
            </p>
          </div>
        </div>
        <span
          className={`rounded-full px-4 py-2 text-sm font-bold ring-1 ${riskBadgeClass(subject.riskLevel)}`}
        >
          {riskLabels[subject.riskLevel]}
        </span>
      </div>
      <div className="grid gap-5 lg:grid-cols-2">
        <section className={cardClass}>
          <h2 className="flex items-center gap-2 text-xl font-bold">
            <Icon name="shield" />
            현재 상태
          </h2>
          <dl className="mt-5 divide-y divide-stone-100">
            {[
              ["위험 점수", `${subject.riskScore}점`],
              ["최근 가전 활동", subject.lastActivity?.applianceType ?? "기록 없음"],
              ["활동 시각", formatDateTime(subject.lastActivity?.occurredAt)],
              ["상태 갱신", formatDateTime(subject.updatedAt)],
            ].map(([term, value]) => (
              <div className="flex justify-between gap-4 py-3" key={term}>
                <dt className="text-stone-500">{term}</dt>
                <dd className="text-right font-semibold">{value}</dd>
              </div>
            ))}
          </dl>
        </section>
        <section className={cardClass}>
          <h2 className="flex items-center gap-2 text-xl font-bold">
            <Icon name="phone" />
            연락 및 알림 응답
          </h2>
          <dl className="mt-5 divide-y divide-stone-100">
            <div className="flex justify-between py-3">
              <dt className="text-stone-500">전화번호</dt>
              <dd className="font-semibold">
                {subject.phone ? (
                  <a className="text-brand-700 underline" href={telephoneHref(subject.phone)}>
                    {subject.phone}
                  </a>
                ) : (
                  "등록되지 않음"
                )}
              </dd>
            </div>
            <div className="flex justify-between py-3">
              <dt className="text-stone-500">최근 응답</dt>
              <dd className="font-semibold">
                {responseLabel(subject.latestAlert?.subjectResponse)}
              </dd>
            </div>
            <div className="flex justify-between py-3">
              <dt className="text-stone-500">응답 시각</dt>
              <dd className="font-semibold">
                {formatDateTime(subject.latestAlert?.subjectResponse.respondedAt)}
              </dd>
            </div>
          </dl>
        </section>
      </div>
      <section className={cardClass}>
        <h2 className="flex items-center gap-2 text-xl font-bold">
          <Icon name="trend" />
          일별 위험 점수 추이
        </h2>
        {scores.length ? (
          <svg
            className="mt-5 w-full overflow-visible"
            viewBox="0 0 600 190"
            role="img"
            aria-label={scores.map((point) => `${point.date} ${point.score}점`).join(", ")}
          >
            <path d="M30 25H570M30 87H570M30 150H570" stroke="#e7e5e4" />
            <polyline points={points} fill="none" stroke="#f37929" strokeWidth="3" />
            {scores.map((point, index) => (
              <g key={point.date}>
                <circle
                  cx={30 + (index * 540) / Math.max(1, scores.length - 1)}
                  cy={150 - (point.score / maximum) * 125}
                  r="4"
                  fill="#f37929"
                />
                <text
                  className="fill-stone-500 text-xs"
                  x={30 + (index * 540) / Math.max(1, scores.length - 1)}
                  y="176"
                  textAnchor="middle"
                >
                  {point.date.slice(5)}
                </text>
              </g>
            ))}
          </svg>
        ) : (
          <p className="mt-5 text-stone-500">아직 기록된 점수 추이가 없습니다.</p>
        )}
      </section>
      <PowerUsage subject={subject} />
      <EventHistory subject={subject} />
    </div>
  );
}
