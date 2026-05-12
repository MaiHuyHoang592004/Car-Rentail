"use client";

import Link from "next/link";
import { useMemo, useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { HostListingRow } from "@/features/host/components/host-listing-row";
import {
  HOST_LISTING_STATUS_FILTERS,
  getHostListingsByStatus,
  type HostListingFilterValue,
} from "@/mocks/host-listings";

export function HostListingsPageView() {
  const [statusFilter, setStatusFilter] = useState<HostListingFilterValue>("ALL");
  const listings = useMemo(() => getHostListingsByStatus(statusFilter), [statusFilter]);

  return (
    <AppShell activePath="/host/listings">
      <div className="space-y-6">
        <PageHeader
          title="Host Listings"
          description="Manage listing lifecycle with static status filters and action hints."
          actions={
            <Link
              href="/host/listings/new"
              className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
            >
              Create listing
            </Link>
          }
        />

        <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <div className="flex flex-wrap gap-2">
            {HOST_LISTING_STATUS_FILTERS.map((status) => {
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

        {listings.length === 0 ? (
          <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
            <h2 className="text-xl font-bold text-foreground">No listings in this status</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Switch filter or create a new listing to continue.
            </p>
          </section>
        ) : (
          <div className="space-y-3">
            {listings.map((listing) => (
              <HostListingRow key={listing.id} listing={listing} />
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}
