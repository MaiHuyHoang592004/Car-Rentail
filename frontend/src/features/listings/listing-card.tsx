import Link from "next/link";

import { StatusBadge } from "@/components/rentflow/status-badge";
import type { ListingCardViewModel } from "@/features/listings/types";

type ListingCardProps = {
  listing: ListingCardViewModel;
};

export function ListingCard({ listing }: ListingCardProps) {
  return (
    <article className="group overflow-hidden rounded-xl border border-border bg-card shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md">
      <div className="relative h-44 overflow-hidden">
        <img
          src={listing.coverImageUrl}
          alt={listing.title}
          className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
        />
        <div className="absolute left-3 top-3">
          <StatusBadge status={listing.status} className="bg-white/90 text-slate-900" />
        </div>
      </div>

      <div className="space-y-3 p-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            {listing.city}
          </p>
          <h3 className="mt-1 text-lg font-bold text-foreground">{listing.title}</h3>
          <p className="mt-1 text-xs text-muted-foreground">{listing.ratingLabel}</p>
        </div>

        <div className="grid grid-cols-2 gap-2 text-xs text-muted-foreground">
          <p>{listing.category}</p>
          <p>{listing.seats} seats</p>
          <p>{listing.transmission}</p>
          <p>{listing.fuelType}</p>
        </div>

        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            <span className="text-base font-bold text-foreground">
              {listing.basePricePerDay.toLocaleString("en-US")} {listing.currency}
            </span>{" "}
            / day
          </p>
          <Link
            href={`/listings/${listing.id}`}
            className="rounded-full bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            View
          </Link>
        </div>
      </div>
    </article>
  );
}
