"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { Plus } from "lucide-react";

import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { EmptyState } from "@/components/rentflow/empty-state";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { VehicleRow } from "@/features/host/components/vehicle-row";
import { getHostVehiclesByStatus, type HostVehicleFilterValue } from "@/features/host/vehicles/api";

const FILTERS: { value: HostVehicleFilterValue; label: string }[] = [
  { value: "ALL", label: "Tat ca" },
  { value: "DRAFT", label: "Nhap" },
  { value: "ACTIVE", label: "Hoat dong" },
  { value: "MAINTENANCE", label: "Bao tri" },
  { value: "SUSPENDED", label: "Tam ngung" },
];

export function HostVehiclesPageView() {
  const [statusFilter, setStatusFilter] = useState<HostVehicleFilterValue>("ALL");

  const { data: vehicles = [], isLoading } = useQuery({
    queryKey: ["host", "vehicles", statusFilter],
    queryFn: () => getHostVehiclesByStatus(statusFilter),
  });

  return (
    <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/vehicles">
      <div className="space-y-5">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-foreground">Xe cua toi</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Quan ly dong xe theo trang thai.
            </p>
          </div>
          <Link
            href="/host/vehicles/new"
            className="flex items-center gap-1.5 rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            <Plus className="h-4 w-4" />
            Them xe
          </Link>
        </div>

        {/* Filter chips */}
        <div className="flex flex-wrap gap-2">
          {FILTERS.map((filter) => {
            const active = statusFilter === filter.value;
            return (
              <button
                key={filter.value}
                type="button"
                onClick={() => setStatusFilter(filter.value)}
                className={[
                  "rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors",
                  active
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-border bg-background text-foreground hover:bg-accent",
                ].join(" ")}
              >
                {filter.label}
              </button>
            );
          })}
        </div>

        {/* Vehicle list */}
        {isLoading ? (
          <PageSkeleton message="Dang tai danh sach xe..." />
        ) : vehicles.length === 0 ? (
          <EmptyState
            title="Khong co xe nao o trang thai nay"
            description="Doi bo loc hoac them xe moi de tiep tuc."
          />
        ) : (
          <div className="space-y-3">
            {vehicles.map((vehicle) => (
              <VehicleRow key={vehicle.id} vehicle={vehicle} />
            ))}
          </div>
        )}
      </div>
    </WorkspaceSidebar>
  );
}