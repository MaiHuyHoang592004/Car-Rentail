"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ListChecks } from "lucide-react";

import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { FormError } from "@/components/rentflow/form-error";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { StatusBadge } from "@/components/rentflow/status-badge";
import {
  adminListListings,
} from "@/features/admin/listings/api";
import {
  ADMIN_LISTING_STATUS_FILTERS,
  type AdminListingFilterValue,
} from "@/features/admin/listings/types";
import { getListingStatusLabel } from "@/lib/display-labels";
import { formatMoney } from "@/lib/formatters";

const FILTERS: { value: AdminListingFilterValue; label: string }[] = [
  { value: "ALL", label: "Tat ca" },
  { value: "PENDING_APPROVAL", label: "Cho duyet" },
  { value: "ACTIVE", label: "Hoat dong" },
  { value: "SUSPENDED", label: "Tam ngung" },
  { value: "DRAFT", label: "Nhap" },
  { value: "ARCHIVED", label: "Luu kho" },
];

function statusBadgeClass(status: string): string {
  switch (status) {
    case "PENDING_APPROVAL": return "bg-amber-100 text-amber-800 border-amber-200";
    case "ACTIVE": return "bg-emerald-100 text-emerald-800 border-emerald-200";
    case "SUSPENDED": return "bg-rose-100 text-rose-800 border-rose-200";
    case "DRAFT": return "bg-slate-100 text-slate-800 border-slate-200";
    case "ARCHIVED": return "bg-gray-100 text-gray-600 border-gray-200";
    default: return "bg-slate-100 text-slate-800 border-slate-200";
  }
}

export function AdminListingsPageView() {
  const [statusFilter, setStatusFilter] = useState<AdminListingFilterValue>("PENDING_APPROVAL");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const { data, isLoading, isError } = useQuery({
    queryKey: ["admin", "listings", statusFilter, page],
    queryFn: () =>
      adminListListings({ status: statusFilter, page, size: pageSize }),
  });

  const listings = data?.listings ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <AppShell activePath="/admin/listings">
      <div className="space-y-5">
        {/* Header */}
        <div className="flex items-center gap-2">
          <ListChecks className="h-5 w-5 text-muted-foreground" />
          <div>
            <h1 className="text-2xl font-bold text-foreground">Duyet tin dang</h1>
            <p className="text-sm text-muted-foreground">
              Duyet, tu choi, tam ngung hoac kich hoat lai tin dang.
            </p>
          </div>
        </div>

        {/* Filter chips */}
        <div className="flex flex-wrap gap-2">
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
                {filter.value !== "ALL" && data ? (
                  <span className="ml-1 text-[10px] opacity-70">
                    ({filter.value === "PENDING_APPROVAL" ? (data?.totalElements ?? 0) : ""})
                  </span>
                ) : null}
              </button>
            );
          })}
        </div>

        {/* Content */}
        {isLoading ? (
          <PageSkeleton message="Dang tai danh sach..." />
        ) : isError ? (
          <FormError>Khong tai duoc danh sach tin dang. Vui long thu lai.</FormError>
        ) : listings.length === 0 ? (
          <EmptyState
            title="Khong co tin dang"
            description="Khong tim thay tin dang nao phu hop voi bo loc."
          />
        ) : (
          <>
            {/* Desktop table */}
            <div className="hidden md:block overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
              <table className="w-full min-w-[700px] text-left text-sm">
                <thead>
                  <tr className="border-b border-border bg-muted/50 text-[11px] uppercase tracking-wide text-muted-foreground">
                    <th className="px-4 py-2.5 font-semibold">Tin dang</th>
                    <th className="px-4 py-2.5 font-semibold">Thanh pho</th>
                    <th className="px-4 py-2.5 font-semibold">Gia / ngay</th>
                    <th className="px-4 py-2.5 font-semibold">Trang thai</th>
                    <th className="px-4 py-2.5 font-semibold"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {listings.map((listing) => (
                    <tr key={listing.id} className="hover:bg-muted/30 transition-colors">
                      <td className="px-4 py-3">
                        <Link
                          href={`/admin/listings/${listing.id}`}
                          className="font-semibold text-foreground hover:text-primary hover:underline"
                        >
                          {listing.title}
                        </Link>
                        <p className="text-xs text-muted-foreground">
                          {listing.id.slice(0, 8)}... &middot; {listing.createdAt ? new Date(listing.createdAt).toLocaleDateString("vi-VN") : ""}
                        </p>
                      </td>
                      <td className="px-4 py-3 text-sm text-muted-foreground">{listing.city}</td>
                      <td className="px-4 py-3 text-sm font-medium text-foreground">
                        {formatMoney(listing.basePricePerDay, listing.currency)} / ngay
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge
                          status={listing.status}
                          label={getListingStatusLabel(listing.status)}
                          className={statusBadgeClass(listing.status)}
                        />
                      </td>
                      <td className="px-4 py-3">
                        <Link
                          href={`/admin/listings/${listing.id}`}
                          className="shrink-0 rounded-full bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90"
                        >
                          Xem
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Mobile cards */}
            <div className="space-y-3 md:hidden">
              {listings.map((listing) => (
                <div
                  key={listing.id}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card p-4 shadow-sm"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusBadge
                        status={listing.status}
                        label={getListingStatusLabel(listing.status)}
                        className={statusBadgeClass(listing.status)}
                      />
                      <span className="text-xs text-muted-foreground">{listing.city}</span>
                    </div>
                    <h3 className="mt-1 truncate text-base font-bold text-foreground">
                      {listing.title}
                    </h3>
                    <p className="text-xs text-muted-foreground">
                      {formatMoney(listing.basePricePerDay, listing.currency)} / ngay
                    </p>
                  </div>
                  <Link
                    href={`/admin/listings/${listing.id}`}
                    className="shrink-0 rounded-full bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90"
                  >
                    Xem
                  </Link>
                </div>
              ))}
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-2">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:bg-accent"
                >
                  Trang truoc
                </button>
                <span className="text-xs text-muted-foreground">
                  Trang {page + 1} / {totalPages} ({totalElements} tin)
                </span>
                <button
                  type="button"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  className="rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:bg-accent"
                >
                  Trang sau
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </AppShell>
  );
}