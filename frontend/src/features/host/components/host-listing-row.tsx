"use client";

import Link from "next/link";
import { ListChecks } from "lucide-react";

import { StatusBadge } from "@/components/rentflow/status-badge";
import { getListingStatusLabel } from "@/lib/display-labels";
import { formatMoney } from "@/lib/formatters";
import type { HostListingSummaryViewModel } from "@/features/host/types";

type HostListingRowProps = {
  listing: HostListingSummaryViewModel;
  onResume?: (listingId: string) => void;
  resumePending?: boolean;
};

function nextActionLabel(status: string): string {
  switch (status) {
    case "DRAFT": return "Gui duyet";
    case "PENDING_APPROVAL": return "Cho duyet";
    case "ACTIVE": return "Dang hoat dong";
    case "SUSPENDED": return "Bi tam ngung";
    case "ARCHIVED": return "Da luu kho";
    default: return "Xem chi tiet";
  }
}

export function HostListingRow({ listing, onResume, resumePending = false }: HostListingRowProps) {
  const canResume = listing.status === "SUSPENDED" && onResume;

  return (
    <article className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="min-w-0 flex-1 space-y-1.5">
          <div className="flex items-center gap-2">
            <ListChecks className="h-4 w-4 shrink-0 text-muted-foreground" />
            <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              {listing.city}
            </p>
          </div>
          <h3 className="text-lg font-bold text-foreground truncate">{listing.title}</h3>
          <p className="text-sm text-muted-foreground">
            {listing.vehicleLabel} &middot; {formatMoney(listing.basePricePerDay, listing.currency)} / ngay
          </p>
          <p className="text-xs text-muted-foreground">
            {nextActionLabel(listing.status)}
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <StatusBadge
            status={listing.status}
            label={getListingStatusLabel(listing.status)}
          />
          {canResume ? (
            <button
              type="button"
              disabled={resumePending}
              onClick={() => onResume?.(listing.id)}
              className="rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {resumePending ? "Dang kich hoat..." : "Kich hoat"}
            </button>
          ) : null}
          <Link
            href={`/host/listings/${listing.id}`}
            className="rounded-full bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Xem
          </Link>
          <Link
            href={`/host/listings/${listing.id}/availability`}
            className="rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground hover:bg-accent"
          >
            Lich xe
          </Link>
        </div>
      </div>
    </article>
  );
}
