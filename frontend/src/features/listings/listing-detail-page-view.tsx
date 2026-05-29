"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";

import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { AvailabilityPreview } from "@/features/listings/availability-preview";
import { ListingBookingCard } from "@/features/listings/listing-booking-card";
import { ListingDetailHeader } from "@/features/listings/listing-detail-header";
import { VehicleSpecsPanel } from "@/features/listings/vehicle-specs-panel";
import { getListingDetailById } from "@/features/listings/api";
import type { ListingDetailViewModel } from "@/features/listings/types";

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

        <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
          <div className="min-w-0 flex-1 space-y-5">
            <SpecsAndInfoSection listing={listing} />
            <AvailabilityPreview listing={listing} />
            <GallerySection listing={listing} />
          </div>

          <div className="w-full lg:sticky lg:top-6 lg:w-80 lg:shrink-0">
            <ListingBookingCard listing={listing} />
            <div className="mt-3 hidden lg:block">
              <Link
                href="/listings"
                className="flex w-full items-center justify-center gap-2 rounded-lg border border-border bg-card px-4 py-2.5 text-sm font-semibold text-foreground hover:bg-accent"
              >
                <ArrowLeft className="h-4 w-4" />
                Quay lại danh sách
              </Link>
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}

function SpecsAndInfoSection({ listing }: { listing: ListingDetailViewModel }) {
  return (
    <section className="space-y-5">
      <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
        <h2 className="text-lg font-bold text-foreground">Giới thiệu xe</h2>
        <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
          {listing.description}
        </p>
      </div>
      <VehicleSpecsPanel listing={listing} />
    </section>
  );
}

function GallerySection({ listing }: { listing: ListingDetailViewModel }) {
  if (listing.galleryImageUrls.length === 0) return null;
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h2 className="text-lg font-bold text-foreground">Thư viện ảnh</h2>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {listing.galleryImageUrls.slice(0, 6).map((url) => (
          <img
            key={url}
            src={url}
            alt={listing.title}
            className="h-44 w-full rounded-lg object-cover"
          />
        ))}
      </div>
    </section>
  );
}
