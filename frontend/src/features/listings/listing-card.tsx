import Link from "next/link";
import { CarFront, Fuel, Heart, MapPin, Settings2 } from "lucide-react";

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
      className="group flex flex-col overflow-hidden rounded-xl border border-border bg-white transition-all duration-300 hover:-translate-y-1 hover:shadow-xl"
    >
      <div className="relative aspect-[4/3] overflow-hidden bg-muted">
        <img
          src={listing.coverImageUrl}
          alt={listing.title}
          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
        />
        <div className="absolute left-3 top-3">
          <span className="rounded-full bg-emerald-500 px-3 py-1 text-[11px] font-bold uppercase tracking-[0.05em] text-white">
            Đang hiển thị
          </span>
        </div>
        <span className="absolute right-3 top-3 flex h-8 w-8 items-center justify-center rounded-full bg-white/85 text-rose-600 transition-transform group-hover:scale-110">
          <Heart className="h-4 w-4" />
        </span>
      </div>

      <div className="flex flex-1 flex-col space-y-4 p-4">
        <div>
          <div className="mb-1 flex min-w-0 items-center gap-1 text-xs font-medium text-muted-foreground">
            <MapPin className="h-4 w-4 shrink-0" />
            <span className="truncate">{listing.city}</span>
            <span className="text-border">•</span>
            <span className="truncate">{getCategoryLabel(listing.category)}</span>
          </div>
          <h3 className="truncate text-xl font-semibold text-foreground">{listing.title}</h3>
        </div>

        <div className="flex flex-wrap gap-2">
          <SpecPill icon={<CarFront className="h-4 w-4" />} label={`${listing.seats} chỗ`} />
          <SpecPill icon={<Settings2 className="h-4 w-4" />} label={getTransmissionLabel(listing.transmission)} />
          <SpecPill icon={<Fuel className="h-4 w-4" />} label={getFuelTypeLabel(listing.fuelType)} />
        </div>

        <div className="mt-auto flex items-center justify-between border-t border-border pt-4">
          <p className="min-w-0 text-sm text-muted-foreground">
            <span className="block text-xl font-semibold text-primary">
              {formatMoney(listing.basePricePerDay, listing.currency)}
            </span>
            / ngày
          </p>
          <span className="rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity group-hover:opacity-90">
            Đặt xe
          </span>
        </div>
      </div>
    </Link>
  );
}

function SpecPill({ icon, label }: { icon: React.ReactNode; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-md bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground">
      {icon}
      {label}
    </span>
  );
}

function getCategoryLabel(category: string): string {
  const labels: Record<string, string> = {
    SEDAN: "Xe sedan",
    SUV: "SUV / CUV",
    HATCHBACK: "Hatchback",
    PICKUP: "Xe bán tải",
    VAN: "Van",
    MINIVAN: "Minivan",
    SPORTS: "Thể thao",
    LUXURY: "Hạng sang",
    ECONOMY: "Tiết kiệm",
    MPV: "MPV",
  };
  return labels[category] ?? category;
}
