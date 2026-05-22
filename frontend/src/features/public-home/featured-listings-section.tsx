import { ListingGrid } from "@/features/listings/listing-grid";
import type { ListingCardViewModel } from "@/features/listings/types";

type FeaturedListingsSectionProps = {
  featured: ListingCardViewModel[];
};

export function FeaturedListingsSection({ featured }: FeaturedListingsSectionProps) {
  return (
    <section className="space-y-4">
      <div>
        <h2 className="text-2xl font-bold text-foreground">Featured Fleet</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Curated active vehicles available for rent.
        </p>
      </div>
      <ListingGrid listings={featured} />
    </section>
  );
}
