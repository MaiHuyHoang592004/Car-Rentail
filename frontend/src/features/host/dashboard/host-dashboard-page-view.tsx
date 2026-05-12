import Link from "next/link";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { HostMetricStrip } from "@/features/host/components/host-metric-strip";
import type { HostDashboardMetricsViewModel } from "@/features/host/types";
import { countBlockedDatesAcrossHost } from "@/mocks/host-availability";
import { HOST_LISTINGS } from "@/mocks/host-listings";
import { HOST_VEHICLES } from "@/mocks/vehicles";

function buildMetrics(): HostDashboardMetricsViewModel {
  return {
    totalVehicles: HOST_VEHICLES.length,
    activeListings: HOST_LISTINGS.filter((listing) => listing.status === "ACTIVE").length,
    pendingApprovals: HOST_LISTINGS.filter((listing) => listing.status === "PENDING_APPROVAL").length,
    blockedDates: countBlockedDatesAcrossHost(),
  };
}

export function HostDashboardPageView() {
  const metrics = buildMetrics();
  const vehicleAttention = HOST_VEHICLES.filter((vehicle) =>
    ["MAINTENANCE", "SUSPENDED"].includes(vehicle.status),
  );
  const listingAttention = HOST_LISTINGS.filter((listing) =>
    ["PENDING_APPROVAL", "SUSPENDED"].includes(listing.status),
  );

  return (
    <AppShell activePath="/host/dashboard">
      <div className="space-y-6">
        <PageHeader
          title="Host Dashboard"
          description="Static host overview for fleet, listing lifecycle, and availability management."
        />

        <HostMetricStrip metrics={metrics} />

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <h2 className="text-lg font-bold text-foreground">Quick Actions</h2>
          <div className="mt-3 flex flex-wrap gap-2">
            <Link
              href="/host/vehicles/new"
              className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
            >
              Add vehicle
            </Link>
            <Link
              href="/host/listings/new"
              className="rounded-full bg-secondary px-4 py-2 text-sm font-semibold text-secondary-foreground transition-opacity hover:opacity-90"
            >
              Create listing
            </Link>
            <Link
              href="/host/listings/hst-lst-001/availability"
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Manage availability
            </Link>
          </div>
        </section>

        <div className="grid gap-4 lg:grid-cols-2">
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-lg font-bold text-foreground">Vehicle Attention Queue</h2>
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
                    {vehicle.status} • {vehicle.city}
                  </p>
                </div>
              ))}
            </div>
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-lg font-bold text-foreground">Listing Attention Queue</h2>
            <div className="mt-3 space-y-2">
              {listingAttention.map((listing) => (
                <div
                  key={listing.id}
                  className="rounded-lg border border-border bg-background px-3 py-2 text-sm"
                >
                  <p className="font-semibold text-foreground">{listing.title}</p>
                  <p className="text-muted-foreground">
                    {listing.status} • {listing.city}
                  </p>
                </div>
              ))}
            </div>
          </section>
        </div>
      </div>
    </AppShell>
  );
}
