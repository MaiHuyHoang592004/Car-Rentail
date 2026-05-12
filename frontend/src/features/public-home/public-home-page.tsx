import { AppShell } from "@/components/rentflow/app-shell";
import { MetricTile } from "@/components/rentflow/metric-tile";
import { FeaturedListingsSection } from "@/features/public-home/featured-listings-section";
import { PublicHero } from "@/features/public-home/public-hero";
import { LISTING_CARDS } from "@/mocks/listings";

export function PublicHomePage() {
  return (
    <AppShell activePath="/">
      <div className="space-y-6">
        <PublicHero />

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <MetricTile label="Active Listings" value={String(LISTING_CARDS.length)} />
          <MetricTile label="Customer Flow" value="Ready (Static)" />
          <MetricTile label="Auth Flow" value="Ready (Static)" />
          <MetricTile label="API Wiring" value="Planned FE 8+" />
        </section>

        <FeaturedListingsSection />
      </div>
    </AppShell>
  );
}
