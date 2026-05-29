import type { HostDashboardMetricsViewModel } from "@/features/host/types";

type HostMetricStripProps = {
  metrics: HostDashboardMetricsViewModel;
};

export function HostMetricStrip({ metrics }: HostMetricStripProps) {
  const items = [
    { label: "Tong xe", value: String(metrics.totalVehicles) },
    { label: "Tin dang hoat dong", value: String(metrics.activeListings) },
    { label: "Tin cho duyet", value: String(metrics.pendingApprovals) },
    { label: "Ngay bi chan", value: String(metrics.blockedDates) },
  ];

  return (
    <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {items.map((item) => (
        <article
          key={item.label}
          className="rounded-xl border border-border bg-card px-4 py-3 shadow-sm"
        >
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{item.label}</p>
          <p className="mt-2 text-2xl font-bold text-foreground">{item.value}</p>
        </article>
      ))}
    </section>
  );
}
