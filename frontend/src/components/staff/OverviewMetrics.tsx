import type { Subject, Risk } from "../../types/monitoring";
import { riskLabels } from "../../types/monitoring";

export default function OverviewMetrics({
  subjects,
  loading,
}: {
  subjects: Subject[];
  loading: boolean;
}) {
  const metrics = [
    {
      label: "담당 대상자",
      value: subjects.length,
      valueStyle: "text-stone-900",
    },
    ...(["DANGER", "WARNING", "NORMAL"] as Risk[]).map((risk) => ({
      label: riskLabels[risk],
      value: subjects.filter((subject) => subject.riskLevel === risk).length,
      valueStyle:
        risk === "DANGER"
          ? "text-red-600"
          : risk === "WARNING"
            ? "text-amber-600"
            : "text-emerald-600",
    })),
  ];

  return (
    <section
      className="grid gap-px overflow-hidden rounded-2xl border border-stone-200 bg-stone-200 shadow-sm sm:grid-cols-2 xl:grid-cols-4"
      aria-label="대상자 현황"
    >
      {metrics.map((metric) => (
        <article className="bg-white px-6 py-5" key={metric.label}>
          <span className="text-sm font-medium text-stone-500">{metric.label}</span>
          <strong className={`mt-2 block text-3xl tabular-nums ${metric.valueStyle}`}>
            {loading ? "—" : metric.value}
            <small className="ml-1 text-sm font-semibold text-stone-500">명</small>
          </strong>
        </article>
      ))}
    </section>
  );
}
