import type { Subject, Risk } from "../../types/monitoring";
import { riskLabels } from "../../types/monitoring";
import Icon from "../common/Icon";
import type { IconName } from "../common/Icon";

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
      icon: "users" as IconName,
      style: "bg-brand-100 text-brand-700",
    },
    ...(["DANGER", "WARNING", "NORMAL"] as Risk[]).map((risk) => ({
      label: riskLabels[risk],
      value: subjects.filter((subject) => subject.riskLevel === risk).length,
      icon: "shield" as IconName,
      style:
        risk === "DANGER"
          ? "bg-red-100 text-red-700"
          : risk === "WARNING"
            ? "bg-amber-100 text-amber-700"
            : "bg-emerald-100 text-emerald-700",
    })),
  ];

  return (
    <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="대상자 현황">
      {metrics.map((metric) => (
        <article
          className="flex items-center justify-between rounded-2xl border border-stone-200 bg-white p-5 shadow-sm"
          key={metric.label}
        >
          <div>
            <span className="text-sm text-stone-500">{metric.label}</span>
            <strong className="mt-2 block text-3xl">
              {loading ? "—" : metric.value}
              <small className="ml-1 text-sm">명</small>
            </strong>
          </div>
          <span className={`grid h-11 w-11 place-items-center rounded-xl ${metric.style}`}>
            <Icon name={metric.icon} />
          </span>
        </article>
      ))}
    </section>
  );
}
