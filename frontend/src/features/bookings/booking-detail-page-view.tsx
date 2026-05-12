"use client";

import Link from "next/link";
import { useMemo, useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { BookingStatusBadge } from "@/features/bookings/booking-status-badge";
import { CancelBookingDialog } from "@/features/bookings/cancel-booking-dialog";
import { EditLocationsDialog } from "@/features/bookings/edit-locations-dialog";
import { HoldCountdown } from "@/features/bookings/hold-countdown";
import { LocationSummary } from "@/features/bookings/location-summary";
import { PolicySnapshotPanel } from "@/features/bookings/policy-snapshot-panel";
import { PriceSnapshotPanel } from "@/features/bookings/price-snapshot-panel";
import type { BookingDetailViewModel, BookingStatus, CancelBookingFormState } from "@/features/bookings/types";
import { getBookingDetailById } from "@/mocks/bookings";

const LOCATION_EDITABLE_STATUSES: BookingStatus[] = [
  "HELD",
  "PENDING_HOST_APPROVAL",
  "CONFIRMED",
];

type BookingDetailPageViewProps = {
  bookingId: string;
};

export function BookingDetailPageView({ bookingId }: BookingDetailPageViewProps) {
  const initialBooking = useMemo(() => getBookingDetailById(bookingId), [bookingId]);
  const [booking, setBooking] = useState<BookingDetailViewModel | null>(initialBooking);
  const [editOpen, setEditOpen] = useState<boolean>(false);
  const [cancelOpen, setCancelOpen] = useState<boolean>(false);
  const [banner, setBanner] = useState<string>("");

  if (!booking) {
    return (
      <AppShell activePath="/me/bookings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-3xl font-bold text-foreground">Booking not found</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            This static mock does not include the requested booking id.
          </p>
          <Link
            href="/me/bookings"
            className="mt-4 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Back to my bookings
          </Link>
        </section>
      </AppShell>
    );
  }

  const canEditLocations = LOCATION_EDITABLE_STATUSES.includes(booking.status);
  const canCancel = booking.status === "HELD";

  function handleLocationPatch(next: Partial<{ pickupLocation: string; returnLocation: string }>) {
    setBooking((prev) => {
      if (!prev) {
        return prev;
      }
      return {
        ...prev,
        pickupLocation: next.pickupLocation ?? prev.pickupLocation,
        returnLocation: next.returnLocation ?? prev.returnLocation,
      };
    });
    setBanner("Location fields updated in static UI.");
  }

  function handleCancel(next: CancelBookingFormState) {
    setBooking((prev) => {
      if (!prev) {
        return prev;
      }
      return {
        ...prev,
        status: "CANCELLED",
        holdExpiresAt: undefined,
        cancellationReason: next.reason || "No reason provided",
      };
    });
    setBanner("Booking status changed to CANCELLED in static UI.");
  }

  return (
    <AppShell activePath="/me/bookings">
      <div className="space-y-6">
        <PageHeader
          title={`Booking ${booking.id}`}
          description="Static detail view with location patch and cancel modal interactions."
          actions={
            <Link
              href="/me/bookings"
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Back to list
            </Link>
          }
        />

        {banner ? (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            {banner}
          </section>
        ) : null}

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Listing</p>
              <h2 className="text-xl font-bold text-foreground">{booking.listingTitle}</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                {booking.pickupDate} to {booking.returnDate}
              </p>
            </div>
            <BookingStatusBadge status={booking.status} />
          </div>

          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <div className="rounded-lg border border-border bg-background px-3 py-2">
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Total</p>
              <p className="mt-1 text-sm font-semibold text-foreground">
                {booking.totalAmount.toLocaleString("en-US")} {booking.currency}
              </p>
            </div>
            <div className="rounded-lg border border-border bg-background px-3 py-2">
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Cancellation reason</p>
              <p className="mt-1 text-sm font-semibold text-foreground">
                {booking.cancellationReason || "Not cancelled"}
              </p>
            </div>
          </div>

          {booking.status === "HELD" && booking.holdExpiresAt ? (
            <div className="mt-4">
              <HoldCountdown key={booking.holdExpiresAt} expiresAt={booking.holdExpiresAt} />
            </div>
          ) : null}

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              disabled={!canEditLocations}
              onClick={() => setEditOpen(true)}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:bg-accent"
            >
              Edit locations
            </button>
            <button
              type="button"
              disabled={!canCancel}
              onClick={() => setCancelOpen(true)}
              className="rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
            >
              Cancel booking
            </button>
          </div>
        </section>

        <LocationSummary pickupLocation={booking.pickupLocation} returnLocation={booking.returnLocation} />

        <div className="grid gap-4 lg:grid-cols-2">
          <PriceSnapshotPanel priceSnapshot={booking.priceSnapshot} />
          <PolicySnapshotPanel policySnapshot={booking.policySnapshot} />
        </div>
      </div>

      <EditLocationsDialog
        key={`${booking.id}:${booking.pickupLocation}:${booking.returnLocation}:${editOpen ? "open" : "closed"}`}
        open={editOpen}
        initialValue={{
          pickupLocation: booking.pickupLocation,
          returnLocation: booking.returnLocation,
        }}
        onClose={() => setEditOpen(false)}
        onConfirm={handleLocationPatch}
      />

      <CancelBookingDialog
        key={`${booking.id}:${booking.status}:${cancelOpen ? "open" : "closed"}`}
        open={cancelOpen}
        onClose={() => setCancelOpen(false)}
        onConfirm={handleCancel}
      />
    </AppShell>
  );
}
