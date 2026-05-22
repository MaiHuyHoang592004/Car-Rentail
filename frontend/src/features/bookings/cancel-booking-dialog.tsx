"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";

import {
  cancelBookingSchema,
  sanitizeCancellationReason,
  type CancelBookingFormState,
} from "@/features/bookings/forms";

type CancelBookingDialogProps = {
  open: boolean;
  onClose: () => void;
  onConfirm: (next: CancelBookingFormState) => void;
};

export function CancelBookingDialog({ open, onClose, onConfirm }: CancelBookingDialogProps) {
  const form = useForm<CancelBookingFormState>({
    resolver: zodResolver(cancelBookingSchema),
    defaultValues: { reason: "" },
  });
  const reason = form.watch("reason");

  if (!open) {
    return null;
  }

  function handleConfirm(values: CancelBookingFormState) {
    onConfirm(values);
    onClose();
    form.reset();
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

        <form onSubmit={form.handleSubmit(handleConfirm)}>
          <div className="mt-4">
            <label className="mb-1 block text-sm font-semibold text-foreground">
              Reason (optional, max 500 chars)
            </label>
            <textarea
              {...form.register("reason")}
              rows={4}
              className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
            />
            <p className="mt-1 text-xs text-muted-foreground">
              Character count (sanitized): {sanitizeCancellationReason(reason).length}/500
            </p>
          </div>

          {form.formState.errors.reason ? (
            <p className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {form.formState.errors.reason.message}
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
              type="submit"
              className="rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90"
            >
              Confirm cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
