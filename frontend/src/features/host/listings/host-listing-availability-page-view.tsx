"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { StatusBadge } from "@/components/rentflow/status-badge";
import { AvailabilityDateCell } from "@/features/host/components/availability-date-cell";
import {
  blockAvailabilityDates,
  getHostAvailabilityByListingId,
  unblockAvailabilityDates,
} from "@/features/host/availability/api";
import { getHostListingById } from "@/features/host/listings/api";

type HostListingAvailabilityPageViewProps = {
  listingId: string;
};

function buildDateRange(): { from: string; to: string } {
  const today = new Date();
  const from = today.toISOString().split("T")[0];
  const future = new Date(today);
  future.setDate(future.getDate() + 90);
  const to = future.toISOString().split("T")[0];
  return { from, to };
}

export function HostListingAvailabilityPageView({ listingId }: HostListingAvailabilityPageViewProps) {
  const queryClient = useQueryClient();
  const { from, to } = buildDateRange();

  const { data: listing, isLoading: loadingListing } = useQuery({
    queryKey: ["host", "listings", listingId],
    queryFn: () => getHostListingById(listingId),
  });

  const { data: days = [], isLoading: loadingDays } = useQuery({
    queryKey: ["host", "listings", listingId, "availability", from, to],
    queryFn: () => getHostAvailabilityByListingId(listingId, from, to),
    enabled: !!listing,
  });

  const [selectedDates, setSelectedDates] = useState<string[]>([]);
  const [banner, setBanner] = useState<string>("");

  const { mutate: doBlock, isPending: blocking } = useMutation({
    mutationFn: (dates: string[]) => blockAvailabilityDates(listingId, dates),
    onSuccess: (count) => {
      setSelectedDates([]);
      setBanner(`Blocked ${count} date(s).`);
      queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId, "availability"] });
    },
    onError: (err: Error) => {
      toast.error(err.message ?? "Failed to block dates.");
    },
  });

  const { mutate: doUnblock, isPending: unblocking } = useMutation({
    mutationFn: (dates: string[]) => unblockAvailabilityDates(listingId, dates),
    onSuccess: (count) => {
      setSelectedDates([]);
      setBanner(`Unblocked ${count} date(s).`);
      queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId, "availability"] });
    },
    onError: (err: Error) => {
      toast.error(err.message ?? "Failed to unblock dates.");
    },
  });

  if (loadingListing) {
    return (
      <AppShell activePath="/host/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">Loading...</p>
        </section>
      </AppShell>
    );
  }

  if (!listing) {
    return (
      <AppShell activePath="/host/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-3xl font-bold text-foreground">Listing not found</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            This listing does not exist or you do not have access.
          </p>
          <Link
            href="/host/listings"
            className="mt-4 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Back to listings
          </Link>
        </section>
      </AppShell>
    );
  }

  function toggleDate(date: string) {
    setSelectedDates((prev) =>
      prev.includes(date) ? prev.filter((v) => v !== date) : [...prev, date],
    );
    setBanner("");
  }

  function handleBlock() {
    if (selectedDates.length === 0) return;
    doBlock(selectedDates);
  }

  function handleUnblock() {
    if (selectedDates.length === 0) return;
    doUnblock(selectedDates);
  }

  return (
    <AppShell activePath="/host/listings">
      <div className="space-y-6">
        <PageHeader
          title={`Availability: ${listing.title}`}
          description="Select dates and apply block or unblock actions. Changes are reflected immediately."
          actions={
            <Link
              href={`/host/listings/${listing.id}`}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Back to listing
            </Link>
          }
        />

        {banner ? (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            {banner}
          </section>
        ) : null}

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status="FREE" />
            <StatusBadge status="BLOCKED" />
            <StatusBadge status="HOLD" />
            <StatusBadge status="BOOKED" />
          </div>

          <p className="mt-3 text-sm text-muted-foreground">
            {selectedDates.length} date(s) selected
          </p>

          {loadingDays ? (
            <p className="mt-4 text-sm text-muted-foreground">Loading availability...</p>
          ) : days.length === 0 ? (
            <p className="mt-4 text-sm text-muted-foreground">No availability data for this period.</p>
          ) : (
            <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
              {days.map((day) => (
                <AvailabilityDateCell
                  key={day.date}
                  day={day}
                  selected={selectedDates.includes(day.date)}
                  onToggle={toggleDate}
                />
              ))}
            </div>
          )}

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              onClick={handleBlock}
              disabled={selectedDates.length === 0 || blocking}
              className="rounded-full bg-secondary px-4 py-2 text-sm font-semibold text-secondary-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
            >
              {blocking ? "Blocking..." : "Block selected"}
            </button>
            <button
              type="button"
              onClick={handleUnblock}
              disabled={selectedDates.length === 0 || unblocking}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:bg-accent"
            >
              {unblocking ? "Unblocking..." : "Unblock selected"}
            </button>
            <button
              type="button"
              onClick={() => {
                setSelectedDates([]);
                setBanner("");
              }}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Clear selection
            </button>
          </div>
        </section>
      </div>
    </AppShell>
  );
}
