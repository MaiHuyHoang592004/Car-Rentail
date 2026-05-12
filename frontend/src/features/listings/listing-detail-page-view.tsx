import { AppShell } from "@/components/rentflow/app-shell";
import { AvailabilityPreview } from "@/features/listings/availability-preview";
import { ListingDetailHeader } from "@/features/listings/listing-detail-header";
import { VehicleSpecsPanel } from "@/features/listings/vehicle-specs-panel";
import { getListingDetailById } from "@/mocks/listings";

type ListingDetailPageViewProps = {
  listingId: string;
};

export function ListingDetailPageView({ listingId }: ListingDetailPageViewProps) {
  const listing = getListingDetailById(listingId);

  if (!listing) {
    return (
      <AppShell activePath="/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-3xl font-bold text-foreground">Listing not found</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            This static mock does not include the requested listing id.
          </p>
        </section>
      </AppShell>
    );
  }

  return (
    <AppShell activePath="/listings">
      <div className="space-y-6">
        <ListingDetailHeader listing={listing} />

        <div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
          <AvailabilityPreview listing={listing} />
          <VehicleSpecsPanel listing={listing} />
        </div>

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <h2 className="text-xl font-bold text-foreground">Photo Gallery</h2>
          <div className="mt-4 grid gap-3 sm:grid-cols-3">
            {listing.galleryImageUrls.map((imageUrl) => (
              <img
                key={imageUrl}
                src={imageUrl}
                alt={listing.title}
                className="h-44 w-full rounded-lg object-cover"
              />
            ))}
          </div>
        </section>
      </div>
    </AppShell>
  );
}
