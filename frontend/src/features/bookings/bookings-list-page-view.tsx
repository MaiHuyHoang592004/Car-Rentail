"use client";

import { useMemo, useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { BookingSummaryCard } from "@/features/bookings/booking-summary-card";
import type { BookingListFilterValue } from "@/features/bookings/types";
import { BOOKING_STATUS_FILTERS, getBookingSummariesByStatus } from "@/mocks/bookings";

export function BookingsListPageView() {
  const [statusFilter, setStatusFilter] = useState<BookingListFilterValue>("ALL");
  const rows = useMemo(() => getBookingSummariesByStatus(statusFilter), [statusFilter]);

  return (
    <AppShell activePath="/me/bookings">
      <div className="space-y-6">
        <PageHeader
          title="My Bookings"
          description="Static booking list with status filtering and contract-aligned status options."
        />

        <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <div className="flex flex-wrap gap-2">
            {BOOKING_STATUS_FILTERS.map((status) => {
              const active = status === statusFilter;
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

        {rows.length === 0 ? (
          <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
            <h2 className="text-xl font-bold text-foreground">No bookings in this status</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Change the status filter to view another booking state.
            </p>
          </section>
        ) : (
          <div className="space-y-3">
            {rows.map((booking) => (
              <BookingSummaryCard key={booking.id} booking={booking} />
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}
