"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { PageHeader } from "@/components/rentflow/page-header";
import { BOOKING_STATUS_FILTERS, listMyBookings } from "@/features/bookings/api";
import { BookingSummaryCard } from "@/features/bookings/booking-summary-card";
import type { BookingListFilterValue } from "@/features/bookings/types";
import { getBookingStatusLabel } from "@/lib/display-labels";
import { ApiError } from "@/lib/api-error";

const PAGE_SIZE = 20;

export function BookingsListPageView() {
  const [statusFilter, setStatusFilter] = useState<BookingListFilterValue>("ALL");
  const [page, setPage] = useState<number>(0);

  const query = useQuery({
    queryKey: ["bookings", "me", statusFilter, page],
    queryFn: ({ signal }) =>
      listMyBookings({ status: statusFilter, page, size: PAGE_SIZE }, signal),
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
        <section className="rf-section-card p-6 md:p-8">
          <PageHeader
            title="Chuyến đi của tôi"
            description="Theo dõi toàn bộ booking, trạng thái giữ chỗ và xác nhận từ chủ xe."
          />
        </section>

        <section className="rf-section-card p-4">
          <div className="flex flex-wrap gap-2">
            {BOOKING_STATUS_FILTERS.map((status) => {
              const active = status === statusFilter;
              const label =
                status === "ALL" || status === "HELD"
                  ? status
                  : getBookingStatusLabel(status);
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
                  {label}
                </button>
              );
            })}
          </div>
        </section>

        {query.isLoading ? (
          <section className="rf-section-card border-dashed p-10 text-center">
            <p className="text-sm text-muted-foreground">Dang tai...</p>
          </section>
        ) : null}

        {query.isError ? (
          <ApiErrorPanel error={query.error instanceof ApiError ? query.error : undefined} />
        ) : null}

        {!query.isLoading && !query.isError && rows.length === 0 ? (
          <section className="rf-section-card border-dashed p-10 text-center">
            <h2 className="text-xl font-bold text-foreground">Chua co don nao</h2>
            <p className="mt-2 text-sm text-muted-foreground">Tao don moi tu trang xe.</p>
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
          <div className="flex items-center justify-between rounded-2xl border border-border/70 bg-card px-4 py-4 text-sm">
            <button
              type="button"
              disabled={page === 0 || query.isFetching}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-full border border-border bg-background px-4 py-1.5 font-semibold disabled:opacity-50 hover:enabled:bg-accent"
            >
              Trang truoc
            </button>
            <span className="text-muted-foreground">Trang {page + 1} / {totalPages}</span>
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
