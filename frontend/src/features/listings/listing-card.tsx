import Link from "next/link";
import { ArrowRight } from "lucide-react";

import { formatMoney } from "@/lib/formatters";
import { getFuelTypeLabel, getTransmissionLabel } from "@/lib/display-labels";
import type { ListingCardViewModel } from "@/features/listings/types";

type ListingCardProps = {
  listing: ListingCardViewModel;
};

export function ListingCard({ listing }: ListingCardProps) {
  return (
    <Link
      href={`/listings/${listing.id}`}
      className="group flex flex-col overflow-hidden rounded-xl border border-border bg-card shadow-sm transition-all hover:-translate-y-1 hover:shadow-lg"
    >
      <div className="relative aspect-[16/10] overflow-hidden bg-muted">
        <img
          src={listing.coverImageUrl}
          alt={listing.title}
          className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
        />
        {listing.ratingLabel ? (
          <div className="absolute bottom-3 left-3 flex items-center gap-1 rounded-full bg-black/70 px-2.5 py-1 text-xs font-semibold text-white">
            <svg
              className="h-3 w-3 fill-amber-400 text-amber-400"
              viewBox="0 0 20 20"
              fill="currentColor"
            >
              <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
            </svg>
            {listing.ratingLabel}
          </div>
        ) : null}
      </div>

      <div className="flex flex-1 flex-col p-4">
        <div className="min-w-0 flex-1">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {listing.city}
          </p>
          <h3 className="mt-1 truncate text-base font-bold text-foreground">
            {listing.title}
          </h3>
        </div>

        <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
          <span>{listing.seats} chỗ</span>
          <span className="text-muted-foreground/50">·</span>
          <span>{getTransmissionLabel(listing.transmission)}</span>
          <span className="text-muted-foreground/50">·</span>
          <span>{getFuelTypeLabel(listing.fuelType)}</span>
        </div>

        <div className="mt-4 flex items-center justify-between border-t border-border pt-3">
          <p className="text-sm text-muted-foreground">
            <span className="text-lg font-bold text-foreground">
              {formatMoney(listing.basePricePerDay, listing.currency)}
            </span>{" "}
            / ngày
          </p>
          <span className="flex items-center gap-1 rounded-full bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90">
            Xem chi tiết
            <ArrowRight className="h-3 w-3" />
          </span>
        </div>
      </div>
    </Link>
  );
}
