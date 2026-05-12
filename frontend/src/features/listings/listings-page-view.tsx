"use client";

import { useMemo, useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { ListingFiltersPanel } from "@/features/listings/listing-filters-panel";
import { ListingGrid } from "@/features/listings/listing-grid";
import type { ListingCardViewModel, ListingFilterState } from "@/features/listings/types";
import { DEFAULT_LISTING_FILTERS, LISTING_CARDS } from "@/mocks/listings";

function applyListingFilters(
  listings: ListingCardViewModel[],
  filters: ListingFilterState,
): ListingCardViewModel[] {
  return listings.filter((listing) => {
    if (filters.city && !listing.city.toLowerCase().includes(filters.city.toLowerCase())) {
      return false;
    }
    if (filters.category !== "ALL" && listing.category !== filters.category) {
      return false;
    }
    if (filters.transmission !== "ALL" && listing.transmission !== filters.transmission) {
      return false;
    }
    if (filters.fuelType !== "ALL" && listing.fuelType !== filters.fuelType) {
      return false;
    }
    if (filters.seats && listing.seats < Number(filters.seats)) {
      return false;
    }
    if (filters.minPrice && listing.basePricePerDay < Number(filters.minPrice)) {
      return false;
    }
    if (filters.maxPrice && listing.basePricePerDay > Number(filters.maxPrice)) {
      return false;
    }
    return true;
  });
}

export function ListingsPageView() {
  const [filters, setFilters] = useState<ListingFilterState>(DEFAULT_LISTING_FILTERS);
  const dateRangeInvalid =
    Boolean(filters.pickupDate) &&
    Boolean(filters.returnDate) &&
    filters.returnDate <= filters.pickupDate;

  const filteredListings = useMemo(
    () => applyListingFilters(LISTING_CARDS, filters),
    [filters],
  );

  return (
    <AppShell activePath="/listings">
      <div className="space-y-6">
        <PageHeader
          title="Search Listings"
          description="Browse active vehicles by city, price range, and specs with mock filter behavior."
        />

        <ListingFiltersPanel
          value={filters}
          onChange={setFilters}
          onReset={() => setFilters(DEFAULT_LISTING_FILTERS)}
        />

        {dateRangeInvalid ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
            Return date must be later than pickup date.
          </div>
        ) : null}

        <ListingGrid
          listings={dateRangeInvalid ? [] : filteredListings}
          emptyTitle="No cars match your filters"
          emptyDescription="Change city, dates, or price settings and try again."
        />
      </div>
    </AppShell>
  );
}
