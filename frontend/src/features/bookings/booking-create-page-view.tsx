"use client";

import Link from "next/link";
import { useMemo, useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { HoldCountdown } from "@/features/bookings/hold-countdown";
import type {
  BookingCreateFormErrors,
  BookingCreateFormState,
  BookingDetailViewModel,
} from "@/features/bookings/types";
import { getListingDetailById } from "@/mocks/listings";

const DAY_IN_MS = 24 * 60 * 60 * 1000;

function getTodayIsoDate(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function validateBookingForm(form: BookingCreateFormState): BookingCreateFormErrors {
  const errors: BookingCreateFormErrors = {};

  if (!form.pickupDate) {
    errors.pickupDate = "Pickup date is required.";
  }

  if (!form.returnDate) {
    errors.returnDate = "Return date is required.";
  }

  if (!form.pickupDate || !form.returnDate) {
    return errors;
  }

  const pickupTime = Date.parse(`${form.pickupDate}T00:00:00`);
  const returnTime = Date.parse(`${form.returnDate}T00:00:00`);
  const todayTime = Date.parse(`${getTodayIsoDate()}T00:00:00`);

  if (Number.isNaN(pickupTime) || Number.isNaN(returnTime)) {
    errors.form = "Invalid date format.";
    return errors;
  }

  if (pickupTime < todayTime) {
    errors.pickupDate = "Pickup date cannot be in the past.";
  }

  if (returnTime <= pickupTime) {
    errors.returnDate = "Return date must be later than pickup date.";
    return errors;
  }

  const rentalDays = (returnTime - pickupTime) / DAY_IN_MS;
  if (rentalDays > 30) {
    errors.returnDate = "Rental duration cannot exceed 30 days.";
  }

  return errors;
}

type BookingCreatePageViewProps = {
  listingId: string;
  isGuest: boolean;
};

export function BookingCreatePageView({ listingId, isGuest }: BookingCreatePageViewProps) {
  const listing = getListingDetailById(listingId);
  const [form, setForm] = useState<BookingCreateFormState>({
    pickupDate: "",
    returnDate: "",
    pickupLocation: "",
    returnLocation: "",
    selectedExtraIds: [],
  });
  const [errors, setErrors] = useState<BookingCreateFormErrors>({});
  const [heldBooking, setHeldBooking] = useState<BookingDetailViewModel | null>(null);

  const selectedExtras = useMemo(() => {
    if (!listing) {
      return [];
    }
    return listing.extras.filter((extra) => form.selectedExtraIds.includes(extra.id));
  }, [form.selectedExtraIds, listing]);

  if (!listing) {
    return (
      <AppShell activePath="/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-3xl font-bold text-foreground">Listing not found</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            This static booking route does not include the requested listing id.
          </p>
        </section>
      </AppShell>
    );
  }
  const listingData = listing;

  function updateField(
    field: Exclude<keyof BookingCreateFormState, "selectedExtraIds">,
    value: string,
  ) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined, form: undefined }));
  }

  function toggleExtra(extraId: string) {
    setForm((prev) => {
      if (prev.selectedExtraIds.includes(extraId)) {
        return { ...prev, selectedExtraIds: prev.selectedExtraIds.filter((id) => id !== extraId) };
      }
      return { ...prev, selectedExtraIds: [...prev.selectedExtraIds, extraId] };
    });
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (isGuest) {
      setErrors({ form: "Login is required before creating a booking hold." });
      return;
    }

    const nextErrors = validateBookingForm(form);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    const pickupTime = Date.parse(`${form.pickupDate}T00:00:00`);
    const returnTime = Date.parse(`${form.returnDate}T00:00:00`);
    const rentalDays = (returnTime - pickupTime) / DAY_IN_MS;
    const baseAmount = rentalDays * listingData.basePricePerDay;
    const extraAmount = selectedExtras.reduce((sum, extra) => sum + extra.price, 0);
    const totalAmount = baseAmount + extraAmount;

    const holdExpiresAt = new Date(Date.now() + 15 * 60 * 1000).toISOString();
    const bookingId = `bk-preview-${Date.now()}`;

    setHeldBooking({
      id: bookingId,
      status: "HELD",
      listingId: listingData.id,
      listingTitle: listingData.title,
      pickupDate: form.pickupDate,
      returnDate: form.returnDate,
      pickupLocation: form.pickupLocation.trim() || listingData.address,
      returnLocation: form.returnLocation.trim() || listingData.address,
      totalAmount,
      currency: listingData.currency,
      holdExpiresAt,
      priceSnapshot: {
        rentalDays,
        basePricePerDay: listingData.basePricePerDay,
        baseAmount,
        extraAmount,
        totalAmount,
        currency: listingData.currency,
        extras: selectedExtras.map((extra) => ({
          id: extra.id,
          name: extra.name,
          quantity: 1,
          unitPrice: extra.price,
          totalPrice: extra.price,
          currency: extra.currency,
        })),
      },
      policySnapshot: {
        cancellationPolicy: listingData.cancellationPolicy,
        instantBook: listingData.instantBook,
        dailyKmLimit: listingData.dailyKmLimit,
      },
    });
  }

  return (
    <AppShell activePath="/listings">
      <div className="space-y-6">
        <PageHeader
          title={`Book ${listingData.title}`}
          description="Create a static HELD booking with local validation and mock hold countdown."
        />

        {isGuest ? (
          <section className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-900">
            <p className="text-sm">
              Guest mode: booking hold creation is disabled. Continue to login and keep this destination.
            </p>
            <Link
              href={`/login?next=/listings/${listingData.id}/book`}
              className="mt-3 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
            >
              Login to book
            </Link>
          </section>
        ) : null}

        {heldBooking ? (
          <section className="space-y-4 rounded-xl border border-emerald-200 bg-emerald-50 p-5">
            <h2 className="text-xl font-bold text-emerald-900">Booking hold created</h2>
            <p className="text-sm text-emerald-900">
              Static flow completed. Payment confirmation is not available in Phase 5.
            </p>
            {heldBooking.holdExpiresAt ? (
              <HoldCountdown key={heldBooking.holdExpiresAt} expiresAt={heldBooking.holdExpiresAt} />
            ) : null}
            <div className="rounded-lg border border-emerald-200 bg-white p-3 text-sm">
              <p>
                Booking ID: <span className="font-semibold">{heldBooking.id}</span>
              </p>
              <p className="mt-1">
                Total:{" "}
                <span className="font-semibold">
                  {heldBooking.totalAmount.toLocaleString("en-US")} {heldBooking.currency}
                </span>
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Link
                href="/me/bookings"
                className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
              >
                Go to my bookings
              </Link>
              <button
                type="button"
                onClick={() => setHeldBooking(null)}
                className="rounded-full border border-border bg-white px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
              >
                Create another hold
              </button>
            </div>
          </section>
        ) : null}

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <h2 className="text-lg font-bold text-foreground">Booking form</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Base price: {listingData.basePricePerDay.toLocaleString("en-US")} {listingData.currency} / day
          </p>

          <form onSubmit={handleSubmit} noValidate className="mt-4 space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Pickup date</label>
                <input
                  type="date"
                  value={form.pickupDate}
                  min={getTodayIsoDate()}
                  onChange={(event) => updateField("pickupDate", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.pickupDate ? (
                  <p className="mt-1 text-xs text-rose-700">{errors.pickupDate}</p>
                ) : null}
              </div>
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Return date</label>
                <input
                  type="date"
                  value={form.returnDate}
                  min={form.pickupDate || getTodayIsoDate()}
                  onChange={(event) => updateField("returnDate", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.returnDate ? (
                  <p className="mt-1 text-xs text-rose-700">{errors.returnDate}</p>
                ) : null}
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Pickup location</label>
                <input
                  type="text"
                  value={form.pickupLocation}
                  onChange={(event) => updateField("pickupLocation", event.target.value)}
                  placeholder={listingData.address}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Return location</label>
                <input
                  type="text"
                  value={form.returnLocation}
                  onChange={(event) => updateField("returnLocation", event.target.value)}
                  placeholder={listingData.address}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
              </div>
            </div>

            <div>
              <p className="mb-2 text-sm font-semibold text-foreground">Optional extras</p>
              <div className="grid gap-2 sm:grid-cols-2">
                {listingData.extras.map((extra) => {
                  const checked = form.selectedExtraIds.includes(extra.id);
                  return (
                    <label
                      key={extra.id}
                      className="flex items-center justify-between rounded-lg border border-border bg-background px-3 py-2 text-sm"
                    >
                      <span>{extra.name}</span>
                      <span className="flex items-center gap-2">
                        <span className="text-muted-foreground">
                          {extra.price.toLocaleString("en-US")} {extra.currency}
                        </span>
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggleExtra(extra.id)}
                          className="size-4 rounded border-input"
                        />
                      </span>
                    </label>
                  );
                })}
              </div>
            </div>

            {errors.form ? (
              <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
                {errors.form}
              </p>
            ) : null}

            <div className="flex flex-wrap gap-2">
              <button
                type="submit"
                className="rounded-full bg-secondary px-5 py-2.5 text-sm font-semibold text-secondary-foreground transition-opacity hover:opacity-90"
              >
                Hold this car
              </button>
              <Link
                href={`/listings/${listingData.id}`}
                className="rounded-full border border-border bg-background px-5 py-2.5 text-sm font-semibold text-foreground hover:bg-accent"
              >
                Back to listing
              </Link>
            </div>
          </form>
        </section>
      </div>
    </AppShell>
  );
}
