"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { HostMetricStrip } from "@/features/host/components/host-metric-strip";
import { getHostVehiclesByStatus } from "@/features/host/vehicles/api";
import { getHostListings } from "@/features/host/listings/api";

export function HostDashboardPageView() {
  const { data: vehicles = [], isLoading: loadingVehicles } = useQuery({
    queryKey: ["host", "vehicles"],
    queryFn: () => getHostVehiclesByStatus(),
  });

  const { data: listingsResult, isLoading: loadingListings } = useQuery({
    queryKey: ["host", "listings"],
    queryFn: () => getHostListings(undefined, 0, 100),
  });

  const vehicleAttention = vehicles.filter((v) =>
    ["MAINTENANCE", "SUSPENDED"].includes(v.status),
  );
  const listingAttention = (listingsResult?.listings ?? []).filter((l) =>
    ["PENDING_APPROVAL", "SUSPENDED"].includes(l.status),
  );

  const totalVehicles = vehicles.length;
  const totalListings = listingsResult?.listings.length ?? 0;

  const metrics = {
    totalVehicles,
    activeListings: totalListings,
    pendingApprovals: (listingsResult?.listings ?? []).filter(
      (l) => l.status === "PENDING_APPROVAL",
    ).length,
    blockedDates: 0,
  };

  return (
    <AppShell activePath="/host/dashboard">
      <div className="space-y-6">
        <PageHeader
          title="Bảng điều khiển Chủ xe"
          description="Tổng quan đội xe, tin đăng và các thao tác đang chờ xử lý."
        />

        <HostMetricStrip metrics={metrics} />

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <h2 className="text-lg font-bold text-foreground">Thao tác nhanh</h2>
          <div className="mt-3 flex flex-wrap gap-2">
            <Link
              href="/host/vehicles/new"
              className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
            >
              Thêm xe
            </Link>
            <Link
              href="/host/listings/new"
              className="rounded-full bg-secondary px-4 py-2 text-sm font-semibold text-secondary-foreground transition-opacity hover:opacity-90"
            >
              Tạo tin đăng
            </Link>
          </div>
        </section>

        <div className="grid gap-4 lg:grid-cols-2">
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-lg font-bold text-foreground">Xe cần xử lý</h2>
            {loadingVehicles ? (
              <p className="mt-3 text-sm text-muted-foreground">Đang tải...</p>
            ) : vehicleAttention.length === 0 ? (
              <p className="mt-3 text-sm text-muted-foreground">Không có xe nào cần xử lý.</p>
            ) : (
              <div className="mt-3 space-y-2">
                {vehicleAttention.map((vehicle) => (
                  <div
                    key={vehicle.id}
                    className="rounded-lg border border-border bg-background px-3 py-2 text-sm"
                  >
                    <p className="font-semibold text-foreground">
                      {vehicle.make} {vehicle.model} ({vehicle.year})
                    </p>
                    <p className="text-muted-foreground">
                      {vehicle.status} &bull; {vehicle.city}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-lg font-bold text-foreground">Tin đăng cần xử lý</h2>
            {loadingListings ? (
              <p className="mt-3 text-sm text-muted-foreground">Đang tải...</p>
            ) : listingAttention.length === 0 ? (
              <p className="mt-3 text-sm text-muted-foreground">Không có tin đăng nào cần xử lý.</p>
            ) : (
              <div className="mt-3 space-y-2">
                {listingAttention.map((listing) => (
                  <div
                    key={listing.id}
                    className="rounded-lg border border-border bg-background px-3 py-2 text-sm"
                  >
                    <p className="font-semibold text-foreground">{listing.title}</p>
                    <p className="text-muted-foreground">
                      {listing.status} &bull; {listing.city}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </AppShell>
  );
}
