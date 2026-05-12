import { ListingGrid } from "@/features/listings/listing-grid";
import { LISTING_CARDS } from "@/mocks/listings";

export function FeaturedListingsSection() {
  const featured = LISTING_CARDS.filter((item) => item.status === "ACTIVE").slice(0, 3);

  return (
    <section className="space-y-4">
      <div>
        <h2 className="text-2xl font-bold text-foreground">Featured Fleet</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Curated active vehicles from the static Phase 5 mock dataset.
        </p>
      </div>
      <ListingGrid listings={featured} />
    </section>
  );
}
