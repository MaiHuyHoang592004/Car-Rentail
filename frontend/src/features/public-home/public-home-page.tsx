"use client";

import { useQuery } from "@tanstack/react-query";
import { AppShell } from "@/components/rentflow/app-shell";
import { MetricTile } from "@/components/rentflow/metric-tile";
import { FeaturedListingsSection } from "@/features/public-home/featured-listings-section";
import { PublicHero } from "@/features/public-home/public-hero";
import { getFeaturedListings } from "@/features/listings/api";

export function PublicHomePage() {
  const { data: featured = [] } = useQuery({
    queryKey: ["listings", "featured"],
    queryFn: () => getFeaturedListings(3),
  });

  return (
    <AppShell activePath="/">
      <div className="space-y-6">
        <PublicHero />

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <MetricTile label="Active Listings" value={String(featured.length > 0 ? featured.length : "—")} />
          <MetricTile label="Customer Flow" value="Ready (Static)" />
          <MetricTile label="Auth Flow" value="Ready (Static)" />
          <MetricTile label="API Wiring" value="In Progress" />
        </section>

        <FeaturedListingsSection featured={featured} />
      </div>
    </AppShell>
  );
}
