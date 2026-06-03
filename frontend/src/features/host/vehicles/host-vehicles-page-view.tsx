"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { Plus } from "lucide-react";

import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { EmptyState } from "@/components/rentflow/empty-state";
import { FormError } from "@/components/rentflow/form-error";
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

const PAGE_SIZE = 20;

export function HostVehiclesPageView() {
  const searchParams = useSearchParams();
  const [statusFilter, setStatusFilter] = useState<HostVehicleFilterValue>(() => {
    const requestedStatus = searchParams.get("status");
    return FILTERS.some((filter) => filter.value === requestedStatus)
      ? (requestedStatus as HostVehicleFilterValue)
      : "ALL";
  });
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, isFetching } = useQuery({
    queryKey: ["host", "vehicles", statusFilter, page],
    queryFn: () => getHostVehiclesByStatus(statusFilter, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });

  const vehicles = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/vehicles">
      <div className="space-y-5">
        <section className="rf-section-card p-6 md:p-8">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Quản lý xe của tôi</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Theo dõi toàn bộ xe theo trạng thái hoạt động, bảo trì và tạm ngưng.
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
        </section>

        <div className="rf-section-card flex flex-wrap gap-2 p-4">
          {FILTERS.map((filter) => {
            const active = statusFilter === filter.value;
            return (
              <button
                key={filter.value}
                type="button"
                onClick={() => {
                  setStatusFilter(filter.value);
                  setPage(0);
                }}
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

        {isLoading ? (
          <PageSkeleton message="Dang tai danh sach xe..." />
        ) : isError ? (
          <FormError>Khong tai duoc danh sach xe. Vui long thu lai.</FormError>
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

        {totalPages > 1 ? (
          <div className="flex items-center justify-between rounded-2xl border border-border/70 bg-card px-4 py-4 text-sm">
            <button
              type="button"
              disabled={page === 0 || isFetching}
              onClick={() => setPage((current) => Math.max(0, current - 1))}
              className="rounded-full border border-border bg-background px-4 py-1.5 font-semibold disabled:opacity-50 hover:enabled:bg-accent"
            >
              Trang truoc
            </button>
            <span className="text-muted-foreground">Trang {page + 1} / {totalPages}</span>
            <button
              type="button"
              disabled={page >= totalPages - 1 || isFetching}
              onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
              className="rounded-full border border-border bg-background px-4 py-1.5 font-semibold disabled:opacity-50 hover:enabled:bg-accent"
            >
              Trang sau
            </button>
          </div>
        ) : null}
      </div>
    </WorkspaceSidebar>
  );
}
