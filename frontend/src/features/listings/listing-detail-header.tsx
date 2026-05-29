import { Car, Users, Cog, Fuel } from "lucide-react";

import { getFuelTypeLabel, getTransmissionLabel } from "@/lib/display-labels";
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
        <div className="absolute inset-0 bg-gradient-to-t from-slate-950/80 via-slate-900/30 to-transparent" />

        <div className="absolute bottom-5 left-5 right-5">
          <p className="text-sm font-semibold uppercase tracking-wide text-slate-200">
            {listing.city}
          </p>
          <h1 className="mt-1 text-3xl font-bold text-white sm:text-4xl">
            {listing.title}
          </h1>

          <div className="mt-3 flex flex-wrap gap-2">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-white/30 bg-white/10 px-3 py-1 text-xs font-medium text-white backdrop-blur-sm">
              <Users className="h-3.5 w-3.5" />
              {listing.vehicle.seats} chỗ
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-white/30 bg-white/10 px-3 py-1 text-xs font-medium text-white backdrop-blur-sm">
              <Cog className="h-3.5 w-3.5" />
              {getTransmissionLabel(listing.vehicle.transmission)}
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-white/30 bg-white/10 px-3 py-1 text-xs font-medium text-white backdrop-blur-sm">
              <Fuel className="h-3.5 w-3.5" />
              {getFuelTypeLabel(listing.vehicle.fuelType)}
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-white/30 bg-white/10 px-3 py-1 text-xs font-medium text-white backdrop-blur-sm">
              <Car className="h-3.5 w-3.5" />
              {listing.vehicle.year}
            </span>
          </div>
        </div>
      </div>
    </section>
  );
}
