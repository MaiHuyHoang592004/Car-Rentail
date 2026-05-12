import { ListingCard } from "@/features/listings/listing-card";
import type { ListingCardViewModel } from "@/features/listings/types";

type ListingGridProps = {
  listings: ListingCardViewModel[];
  emptyTitle?: string;
  emptyDescription?: string;
};

export function ListingGrid({
  listings,
  emptyTitle = "No listings found",
  emptyDescription = "Try changing city, dates, or price filters.",
}: ListingGridProps) {
  if (listings.length === 0) {
    return (
      <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
        <h3 className="text-xl font-bold text-foreground">{emptyTitle}</h3>
        <p className="mt-2 text-sm text-muted-foreground">{emptyDescription}</p>
      </section>
    );
  }

  return (
    <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {listings.map((listing) => (
        <ListingCard key={listing.id} listing={listing} />
      ))}
    </section>
  );
}
