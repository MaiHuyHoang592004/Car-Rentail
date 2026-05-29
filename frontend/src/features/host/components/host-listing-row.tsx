import Link from "next/link";

import { StatusBadge } from "@/components/rentflow/status-badge";
import { getListingActionHint, getListingStatusLabel } from "@/lib/display-labels";
import { formatMoney } from "@/lib/formatters";
import type { HostListingViewModel } from "@/features/host/types";

type HostListingRowProps = {
  listing: HostListingViewModel;
};

export function HostListingRow({ listing }: HostListingRowProps) {
  return (
    <article className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{listing.city}</p>
          <h3 className="text-lg font-bold text-foreground">{listing.title}</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {listing.vehicleLabel} · {formatMoney(listing.basePricePerDay)} / ngày
          </p>
          <p className="mt-1 text-xs text-muted-foreground">{getListingActionHint(listing.status)}</p>
        </div>

        <div className="flex items-center gap-2">
          <StatusBadge status={listing.status} label={getListingStatusLabel(listing.status)} />
          <Link
            href={`/host/listings/${listing.id}`}
            className="rounded-full bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Mở
          </Link>
          <Link
            href={`/host/listings/${listing.id}/availability`}
            className="rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground hover:bg-accent"
          >
            Tình trạng
          </Link>
        </div>
      </div>
    </article>
  );
}