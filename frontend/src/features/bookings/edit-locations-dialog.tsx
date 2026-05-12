"use client";

import { useState } from "react";

import type {
  BookingLocationPatchFormState,
  BookingLocationPatchPayload,
} from "@/features/bookings/types";

type EditLocationsDialogProps = {
  open: boolean;
  initialValue: BookingLocationPatchFormState;
  onClose: () => void;
  onConfirm: (next: BookingLocationPatchPayload) => void;
};

export function EditLocationsDialog({
  open,
  initialValue,
  onClose,
  onConfirm,
}: EditLocationsDialogProps) {
  const [form, setForm] = useState<BookingLocationPatchFormState>(initialValue);
  const [error, setError] = useState<string>("");

  if (!open) {
    return null;
  }

  function handleConfirm() {
    const pickupLocation = form.pickupLocation.trim();
    const returnLocation = form.returnLocation.trim();

    if (!pickupLocation && !returnLocation) {
      setError("Provide at least one location.");
      return;
    }

    onConfirm({
      ...(pickupLocation ? { pickupLocation } : {}),
      ...(returnLocation ? { returnLocation } : {}),
    });
    onClose();
  }

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/45 p-4">
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-lg rounded-xl border border-border bg-card p-5 shadow-lg"
      >
        <h3 className="text-lg font-bold text-foreground">Edit Locations</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          Update pickup and return locations for the current booking.
        </p>

        <div className="mt-4 space-y-3">
          <div>
            <label className="mb-1 block text-sm font-semibold text-foreground">Pickup location</label>
            <input
              type="text"
              value={form.pickupLocation}
              onChange={(event) => {
                setForm((prev) => ({ ...prev, pickupLocation: event.target.value }));
                setError("");
              }}
              className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-semibold text-foreground">Return location</label>
            <input
              type="text"
              value={form.returnLocation}
              onChange={(event) => {
                setForm((prev) => ({ ...prev, returnLocation: event.target.value }));
                setError("");
              }}
              className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
            />
          </div>
        </div>

        {error ? (
          <p className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {error}
          </p>
        ) : null}

        <div className="mt-4 flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
          >
            Close
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Save locations
          </button>
        </div>
      </div>
    </div>
  );
}
