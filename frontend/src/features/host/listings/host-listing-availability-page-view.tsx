"use client";

import Link from "next/link";
import { useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { StatusBadge } from "@/components/rentflow/status-badge";
import { AvailabilityDateCell } from "@/features/host/components/availability-date-cell";
import type { HostAvailabilityDayViewModel } from "@/features/host/types";
import {
  blockAvailabilityDates,
  getHostAvailabilityByListingId,
  unblockAvailabilityDates,
} from "@/mocks/host-availability";
import { getHostListingById } from "@/mocks/host-listings";

type HostListingAvailabilityPageViewProps = {
  listingId: string;
};

export function HostListingAvailabilityPageView({ listingId }: HostListingAvailabilityPageViewProps) {
  const listing = getHostListingById(listingId);
  const [days, setDays] = useState<HostAvailabilityDayViewModel[]>(() =>
    getHostAvailabilityByListingId(listingId),
  );
  const [selectedDates, setSelectedDates] = useState<string[]>([]);
  const [banner, setBanner] = useState<string>("");

  if (!listing) {
    return (
      <AppShell activePath="/host/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-3xl font-bold text-foreground">Listing not found</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            This static availability screen does not include the requested listing id.
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

  const selectedSet = new Set(selectedDates);
  let freeCount = 0;
  let blockedCount = 0;
  let readOnlyCount = 0;
  days.forEach((day) => {
    if (!selectedSet.has(day.date)) {
      return;
    }
    if (day.status === "FREE") {
      freeCount += 1;
    } else if (day.status === "BLOCKED") {
      blockedCount += 1;
    } else {
      readOnlyCount += 1;
    }
  });

  function toggleDate(date: string) {
    setSelectedDates((prev) => {
      if (prev.includes(date)) {
        return prev.filter((value) => value !== date);
      }
      return [...prev, date];
    });
    setBanner("");
  }

  function handleBlock() {
    const result = blockAvailabilityDates(days, selectedDates);
    setDays(result.nextDays);
    setSelectedDates([]);
    if (result.updatedCount === 0) {
      setBanner("No FREE dates selected. Block action skipped.");
      return;
    }
    if (result.skippedDates.length > 0) {
      setBanner(
        `Blocked ${result.updatedCount} date(s). Skipped ${result.skippedDates.length} non-FREE date(s).`,
      );
      return;
    }
    setBanner(`Blocked ${result.updatedCount} date(s).`);
  }

  function handleUnblock() {
    const result = unblockAvailabilityDates(days, selectedDates);
    setDays(result.nextDays);
    setSelectedDates([]);
    if (result.updatedCount === 0) {
      setBanner("No BLOCKED dates selected. Unblock action skipped.");
      return;
    }
    if (result.skippedDates.length > 0) {
      setBanner(
        `Unblocked ${result.updatedCount} date(s). Skipped ${result.skippedDates.length} non-BLOCKED date(s).`,
      );
      return;
    }
    setBanner(`Unblocked ${result.updatedCount} date(s).`);
  }

  return (
    <AppShell activePath="/host/listings">
      <div className="space-y-6">
        <PageHeader
          title={`Availability: ${listing.title}`}
          description="Select FREE or BLOCKED dates and apply static block/unblock actions."
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
            Selected: {selectedDates.length} date(s), FREE: {freeCount}, BLOCKED: {blockedCount},
            Read-only: {readOnlyCount}
          </p>

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

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              onClick={handleBlock}
              disabled={selectedDates.length === 0}
              className="rounded-full bg-secondary px-4 py-2 text-sm font-semibold text-secondary-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
            >
              Block selected
            </button>
            <button
              type="button"
              onClick={handleUnblock}
              disabled={selectedDates.length === 0}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:bg-accent"
            >
              Unblock selected
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
