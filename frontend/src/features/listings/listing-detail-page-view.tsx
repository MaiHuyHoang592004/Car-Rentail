"use client";

import { useQuery } from "@tanstack/react-query";
import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { AvailabilityPreview } from "@/features/listings/availability-preview";
import { ListingDetailHeader } from "@/features/listings/listing-detail-header";
import { VehicleSpecsPanel } from "@/features/listings/vehicle-specs-panel";
import { getListingDetailById } from "@/features/listings/api";

type ListingDetailPageViewProps = {
  listingId: string;
};

export function ListingDetailPageView({ listingId }: ListingDetailPageViewProps) {
  const { data: listing, isLoading } = useQuery({
    queryKey: ["listings", listingId],
    queryFn: () => getListingDetailById(listingId),
  });

  if (isLoading) {
    return (
      <AppShell activePath="/listings">
        <PageSkeleton message="Đang tải thông tin xe..." />
      </AppShell>
    );
  }

  if (!listing) {
    return (
      <AppShell activePath="/listings">
        <EmptyState
          title="Không tìm thấy xe"
          description="Xe này không tồn tại hoặc không còn khả dụng."
        />
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
          <h2 className="text-xl font-bold text-foreground">Thư viện ảnh</h2>
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
