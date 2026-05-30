"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { getHostOverviewReport } from "@/features/host/reports/api";
import { formatMoney } from "@/lib/formatters";

function defaultRange() {
  const to = new Date();
  const from = new Date();
  from.setDate(to.getDate() - 29);
  return {
    from: from.toISOString().split("T")[0],
    to: to.toISOString().split("T")[0],
  };
}

export function HostReportsPageView() {
  const range = useMemo(() => defaultRange(), []);
  const { data, isLoading } = useQuery({
    queryKey: ["host", "reports", "overview", range.from, range.to],
    queryFn: () => getHostOverviewReport(range.from, range.to),
  });

  const statCards = data ? [
    { label: "Gross captured", value: formatMoney(data.grossCaptured, "VND") },
    { label: "Net earnings", value: formatMoney(data.netEarnings, "VND") },
    { label: "Bookings", value: String(data.bookingCount) },
    { label: "Active listings", value: String(data.activeListings) },
    { label: "Pending approvals", value: String(data.pendingApprovalListings) },
    { label: "Blocked days", value: String(data.blockedDays) },
  ] : [];

  return (
    <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/reports">
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Bao cao host</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Tong hop doanh thu va tinh trang availability trong 30 ngay gan nhat.
          </p>
        </div>

        {isLoading || !data ? (
          <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center text-sm text-muted-foreground">
            Dang tai bao cao...
          </section>
        ) : (
          <>
            <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {statCards.map((card) => (
                <article key={card.label} className="rounded-xl border border-border bg-card px-4 py-4 shadow-sm">
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{card.label}</p>
                  <p className="mt-2 text-2xl font-bold text-foreground">{card.value}</p>
                </article>
              ))}
            </section>

            <section className="grid gap-4 lg:grid-cols-2">
              <article className="rounded-xl border border-border bg-card p-5 shadow-sm">
                <h2 className="text-base font-semibold text-foreground">Availability</h2>
                <div className="mt-4 space-y-3 text-sm">
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Generated days</span>
                    <strong className="text-foreground">{data.generatedDays}</strong>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">HOLD days</span>
                    <strong className="text-foreground">{data.holdDays}</strong>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">BOOKED days</span>
                    <strong className="text-foreground">{data.bookedDays}</strong>
                  </div>
                </div>
              </article>

              <article className="rounded-xl border border-border bg-card p-5 shadow-sm">
                <h2 className="text-base font-semibold text-foreground">Ty le</h2>
                <div className="mt-4 space-y-3 text-sm">
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Occupancy rate</span>
                    <strong className="text-foreground">{data.occupancyRate.toFixed(2)}%</strong>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Blocked rate</span>
                    <strong className="text-foreground">{data.blockedRate.toFixed(2)}%</strong>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">Window</span>
                    <strong className="text-foreground">{data.from} {"->"} {data.to}</strong>
                  </div>
                </div>
              </article>
            </section>
          </>
        )}
      </div>
    </WorkspaceSidebar>
  );
}
