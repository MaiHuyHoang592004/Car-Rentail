"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { PageHeader } from "@/components/rentflow/page-header";
import { BOOKING_STATUS_FILTERS, listMyBookings } from "@/features/bookings/api";
import { BookingSummaryCard } from "@/features/bookings/booking-summary-card";
import { RoleGuard } from "@/features/auth/role-guard";
import type { BookingListFilterValue } from "@/features/bookings/types";
import { ApiError } from "@/lib/api-error";

const PAGE_SIZE = 20;

export function BookingsListPageView() {
  return (
    <RoleGuard requiredRoles={["CUSTOMER"]}>
      <BookingsListContent />
    </RoleGuard>
  );
}

function BookingsListContent() {
  const [statusFilter, setStatusFilter] = useState<BookingListFilterValue>("ALL");
  const [page, setPage] = useState<number>(0);

  const query = useQuery({
    queryKey: ["bookings", "me", statusFilter, page],
    queryFn: () => listMyBookings({ status: statusFilter, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  function handleFilterChange(next: BookingListFilterValue) {
    setStatusFilter(next);
    setPage(0);
  }

  const rows = query.data?.content ?? [];
  const totalPages = query.data?.totalPages ?? 0;

  return (
    <AppShell activePath="/me/bookings">
      <div className="space-y-6">
        <PageHeader
          title="My Bookings"
          description="Danh sách booking của bạn, lọc theo trạng thái."
        />

        <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <div className="flex flex-wrap gap-2">
            {BOOKING_STATUS_FILTERS.map((status) => {
              const active = status === statusFilter;
              return (
                <button
                  key={status}
                  type="button"
                  onClick={() => handleFilterChange(status)}
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

        {query.isLoading ? (
          <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
            <p className="text-sm text-muted-foreground">Đang tải bookings...</p>
          </section>
        ) : null}

        {query.isError ? (
          <ApiErrorPanel error={query.error instanceof ApiError ? query.error : undefined} />
        ) : null}

        {!query.isLoading && !query.isError && rows.length === 0 ? (
          <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
            <h2 className="text-xl font-bold text-foreground">Chưa có booking nào</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Đổi bộ lọc trạng thái hoặc tạo booking mới từ trang xe.
            </p>
          </section>
        ) : null}

        {rows.length > 0 ? (
          <div className="space-y-3">
            {rows.map((booking) => (
              <BookingSummaryCard key={booking.id} booking={booking} />
            ))}
          </div>
        ) : null}

        {totalPages > 1 ? (
          <div className="flex items-center justify-between text-sm">
            <button
              type="button"
              disabled={page === 0 || query.isFetching}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-full border border-border bg-background px-4 py-1.5 font-semibold disabled:opacity-50 hover:enabled:bg-accent"
            >
              Trang trước
            </button>
            <span className="text-muted-foreground">
              Trang {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              disabled={page >= totalPages - 1 || query.isFetching}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-full border border-border bg-background px-4 py-1.5 font-semibold disabled:opacity-50 hover:enabled:bg-accent"
            >
              Trang sau
            </button>
          </div>
        ) : null}
      </div>
    </AppShell>
  );
}
