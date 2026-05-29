"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { FormError } from "@/components/rentflow/form-error";
import { PageHeader } from "@/components/rentflow/page-header";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import {
  adminListListings,
} from "@/features/admin/listings/api";
import {
  ADMIN_LISTING_STATUS_FILTERS,
  type AdminListingFilterValue,
} from "@/features/admin/listings/types";

/* ------------------------------------------------------------------ */
/*  Helpers                                                           */
/* ------------------------------------------------------------------ */

function statusBadgeClass(status: string): string {
  switch (status) {
    case "PENDING_APPROVAL":
      return "bg-amber-100 text-amber-800 border-amber-200";
    case "ACTIVE":
      return "bg-emerald-100 text-emerald-800 border-emerald-200";
    case "SUSPENDED":
      return "bg-rose-100 text-rose-800 border-rose-200";
    case "DRAFT":
      return "bg-slate-100 text-slate-800 border-slate-200";
    case "ARCHIVED":
      return "bg-gray-100 text-gray-600 border-gray-200";
    default:
      return "bg-slate-100 text-slate-800 border-slate-200";
  }
}

/* ------------------------------------------------------------------ */
/*  Page View                                                         */
/* ------------------------------------------------------------------ */

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

  return (
    <AppShell activePath="/admin/listings">
      <div className="space-y-6">
        <PageHeader
          title="Duyệt tin đăng"
          description="Duyệt, từ chối, tạm ngưng hoặc kích hoạt lại tin đăng."
        />

        {/* Status filter chips */}
        <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <div className="flex flex-wrap gap-2">
            {ADMIN_LISTING_STATUS_FILTERS.map((status) => {
              const active = statusFilter === status;
              return (
                <button
                  key={status}
                  type="button"
                  onClick={() => {
                    setStatusFilter(status);
                    setPage(0);
                  }}
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

        {/* Loading / Error / Empty / List */}
        {isLoading ? (
          <PageSkeleton message="Đang tải danh sách..." />
        ) : isError ? (
          <FormError>Không tải được danh sách tin đăng. Vui lòng thử lại.</FormError>
        ) : listings.length === 0 ? (
          <EmptyState
            title="Không có tin đăng"
            description="Không tìm thấy tin đăng nào phù hợp với bộ lọc."
          />
        ) : (
          <div className="space-y-3">
            {listings.map((listing) => (
              <div
                key={listing.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card p-4 shadow-sm"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span
                      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-[11px] font-semibold ${statusBadgeClass(listing.status)}`}
                    >
                      {listing.status}
                    </span>
                    <span className="text-xs text-muted-foreground">{listing.city}</span>
                  </div>
                  <h3 className="mt-1 truncate text-base font-bold text-foreground">
                    {listing.title}
                  </h3>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {listing.basePricePerDay.toLocaleString("en-US")} {listing.currency}/day
                    &nbsp;·&nbsp;{listing.id.slice(0, 8)}…
                  </p>
                </div>
                <Link
                  href={`/admin/listings/${listing.id}`}
                  className="shrink-0 rounded-full bg-primary px-4 py-1.5 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90"
                >
                  Chi tiết
                </Link>
              </div>
            ))}
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 pt-2">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:bg-accent"
            >
              ← Trước
            </button>
            <span className="text-xs text-muted-foreground">
              Trang {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              className="rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:bg-accent"
            >
              Sau →
            </button>
          </div>
        )}
      </div>
    </AppShell>
  );
}
