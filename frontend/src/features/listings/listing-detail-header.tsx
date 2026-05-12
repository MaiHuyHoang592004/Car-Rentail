import Link from "next/link";

import { StatusBadge } from "@/components/rentflow/status-badge";
import type { ListingDetailViewModel } from "@/features/listings/types";

type ListingDetailHeaderProps = {
  listing: ListingDetailViewModel;
};

export function ListingDetailHeader({ listing }: ListingDetailHeaderProps) {
  return (
    <section className="overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
      <div className="relative h-72 overflow-hidden sm:h-96">
        <img
          src={listing.coverImageUrl}
          alt={listing.title}
          className="h-full w-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-slate-950/70 via-slate-900/20 to-transparent" />

        <div className="absolute left-4 top-4">
          <StatusBadge status={listing.status} className="bg-white/90 text-slate-900" />
        </div>

        <div className="absolute bottom-5 left-5 right-5">
          <p className="text-sm font-semibold uppercase tracking-wide text-slate-100">{listing.city}</p>
          <h1 className="mt-1 text-3xl font-bold text-white sm:text-4xl">{listing.title}</h1>
          <p className="mt-2 max-w-3xl text-sm text-slate-200">{listing.description}</p>
        </div>
      </div>

      <div className="flex flex-col gap-4 border-t border-border bg-background p-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Base price</p>
          <p className="text-2xl font-bold text-foreground">
            {listing.basePricePerDay.toLocaleString("en-US")} {listing.currency}
            <span className="ml-1 text-sm font-medium text-muted-foreground">/ day</span>
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link
            href="/listings"
            className="rounded-full border border-border bg-card px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
          >
            Back to listings
          </Link>
          <Link
            href={`/login?next=/listings/${listing.id}/book`}
            className="rounded-full bg-secondary px-4 py-2 text-sm font-semibold text-secondary-foreground transition-opacity hover:opacity-90"
          >
            Book now
          </Link>
        </div>
      </div>
    </section>
  );
}
