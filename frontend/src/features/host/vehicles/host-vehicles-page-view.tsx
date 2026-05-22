"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { VehicleRow } from "@/features/host/components/vehicle-row";
import { HOST_VEHICLE_STATUS_FILTERS, getHostVehiclesByStatus, type HostVehicleFilterValue } from "@/features/host/vehicles/api";

export function HostVehiclesPageView() {
  const [statusFilter, setStatusFilter] = useState<HostVehicleFilterValue>("ALL");

  const { data: vehicles = [], isLoading } = useQuery({
    queryKey: ["host", "vehicles", statusFilter],
    queryFn: () => getHostVehiclesByStatus(statusFilter),
  });

  return (
    <AppShell activePath="/host/vehicles">
      <div className="space-y-6">
        <PageHeader
          title="Xe của tôi"
          description="Quản lý đội xe theo trạng thái và thao tác."
          actions={
            <Link
              href="/host/vehicles/new"
              className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
            >
              Thêm xe
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

        {isLoading ? (
          <section className="rounded-xl border border-border bg-card p-10 text-center">
            <p className="text-sm text-muted-foreground">Đang tải danh sách xe...</p>
          </section>
        ) : vehicles.length === 0 ? (
          <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
            <h2 className="text-xl font-bold text-foreground">Không có xe nào ở trạng thái này</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Đổi bộ lọc hoặc thêm xe mới để tiếp tục.
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
