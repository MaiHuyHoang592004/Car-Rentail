"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { DEFAULT_LISTING_FILTERS, searchListings } from "@/features/listings/api";
import { ListingFiltersPanel } from "@/features/listings/listing-filters-panel";
import { ListingGrid } from "@/features/listings/listing-grid";
import type { ListingFilterState } from "@/features/listings/types";
import { ApiError } from "@/lib/api-error";

const PAGE_SIZE = 20;

export function ListingsPageView() {
  const [filters, setFilters] = useState<ListingFilterState>(DEFAULT_LISTING_FILTERS);
  const [page, setPage] = useState(0);
  const dateRangeInvalid =
    Boolean(filters.pickupDate) &&
    Boolean(filters.returnDate) &&
    filters.returnDate <= filters.pickupDate;

  const query = useQuery({
    queryKey: ["listings", "search", filters, page],
    queryFn: () => searchListings(filters, page, PAGE_SIZE),
    enabled: !dateRangeInvalid,
    placeholderData: keepPreviousData,
  });

  function handleFiltersChange(next: ListingFilterState) {
    setFilters(next);
    setPage(0);
  }

  function handleReset() {
    setFilters(DEFAULT_LISTING_FILTERS);
    setPage(0);
  }

  const listings = query.data?.content ?? [];
  const totalPages = query.data?.totalPages ?? 0;

  return (
    <AppShell activePath="/listings">
      <div className="space-y-6">
        <PageHeader
          title="Search Listings"
          description="Browse active vehicles by city, price range, and specs."
        />

        <ListingFiltersPanel
          value={filters}
          onChange={handleFiltersChange}
          onReset={handleReset}
        />

        {dateRangeInvalid ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
            Return date must be later than pickup date.
          </div>
        ) : null}

        {query.isLoading ? (
          <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
            <p className="text-sm text-muted-foreground">Loading listings...</p>
          </section>
        ) : null}

        {query.isError ? (
          <ApiErrorPanel error={query.error instanceof ApiError ? query.error : undefined} />
        ) : null}

        {!query.isLoading && !query.isError ? (
          <ListingGrid
            listings={dateRangeInvalid ? [] : listings}
            emptyTitle="No cars match your filters"
            emptyDescription="Change city, dates, or price settings and try again."
          />
        ) : null}

        {!dateRangeInvalid && totalPages > 1 ? (
          <div className="flex items-center justify-between text-sm">
            <button
              type="button"
              disabled={page === 0 || query.isFetching}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-full border border-border bg-background px-4 py-1.5 font-semibold disabled:opacity-50 hover:enabled:bg-accent"
            >
              Previous
            </button>
            <span className="text-muted-foreground">
              Page {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              disabled={page >= totalPages - 1 || query.isFetching}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-full border border-border bg-background px-4 py-1.5 font-semibold disabled:opacity-50 hover:enabled:bg-accent"
            >
              Next
            </button>
          </div>
        ) : null}
      </div>
    </AppShell>
  );
}
