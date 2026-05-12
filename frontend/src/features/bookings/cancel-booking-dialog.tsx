"use client";

import { useState } from "react";

import type { CancelBookingFormState } from "@/features/bookings/types";

function sanitizeReason(input: string): string {
  return input.replace(/<[^>]*>/g, "").trim();
}

type CancelBookingDialogProps = {
  open: boolean;
  onClose: () => void;
  onConfirm: (next: CancelBookingFormState) => void;
};

export function CancelBookingDialog({ open, onClose, onConfirm }: CancelBookingDialogProps) {
  const [reason, setReason] = useState<string>("");
  const [error, setError] = useState<string>("");

  if (!open) {
    return null;
  }

  function handleConfirm() {
    const sanitized = sanitizeReason(reason);
    if (sanitized.length > 500) {
      setError("Reason must be 500 characters or fewer.");
      return;
    }

    onConfirm({ reason: sanitized });
    onClose();
  }

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/45 p-4">
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-lg rounded-xl border border-border bg-card p-5 shadow-lg"
      >
        <h3 className="text-lg font-bold text-foreground">Cancel Booking</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          This static action updates local UI state to `CANCELLED`.
        </p>

        <div className="mt-4">
          <label className="mb-1 block text-sm font-semibold text-foreground">
            Reason (optional, max 500 chars)
          </label>
          <textarea
            value={reason}
            onChange={(event) => {
              setReason(event.target.value);
              setError("");
            }}
            rows={4}
            className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
          />
          <p className="mt-1 text-xs text-muted-foreground">
            Character count (sanitized): {sanitizeReason(reason).length}/500
          </p>
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
            Keep booking
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            className="rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90"
          >
            Confirm cancel
          </button>
        </div>
      </div>
    </div>
  );
}
