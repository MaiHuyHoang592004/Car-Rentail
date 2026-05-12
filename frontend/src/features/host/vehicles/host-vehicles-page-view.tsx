"use client";

import Link from "next/link";
import { useMemo, useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { VehicleRow } from "@/features/host/components/vehicle-row";
import { HOST_VEHICLE_STATUS_FILTERS, getHostVehiclesByStatus, type HostVehicleFilterValue } from "@/mocks/vehicles";

export function HostVehiclesPageView() {
  const [statusFilter, setStatusFilter] = useState<HostVehicleFilterValue>("ALL");
  const vehicles = useMemo(() => getHostVehiclesByStatus(statusFilter), [statusFilter]);

  return (
    <AppShell activePath="/host/vehicles">
      <div className="space-y-6">
        <PageHeader
          title="Host Vehicles"
          description="Manage fleet inventory with static status filters and action states."
          actions={
            <Link
              href="/host/vehicles/new"
              className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
            >
              Add vehicle
            </Link>
          }
        />

        <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <div className="flex flex-wrap gap-2">
            {HOST_VEHICLE_STATUS_FILTERS.map((status) => {
              const active = statusFilter === status;
              return (
                <button
                  key={status}
                  type="button"
                  onClick={() => setStatusFilter(status)}
                  className={[
                    "rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors",
                    active
                      ? "border-primary bg-primary text-primary-foreground"
                      : "border-border bg-background text-foreground hover:bg-accent",
                  ].join(" ")}
                >
                  {status}
                </button>
              );
            })}
          </div>
        </section>

        {vehicles.length === 0 ? (
          <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
            <h2 className="text-xl font-bold text-foreground">No vehicles in this status</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Change filter or add a new vehicle to continue.
            </p>
          </section>
        ) : (
          <div className="space-y-3">
            {vehicles.map((vehicle) => (
              <VehicleRow key={vehicle.id} vehicle={vehicle} />
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}
