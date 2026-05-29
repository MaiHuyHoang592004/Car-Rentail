"use client";

import Link from "next/link";

import { BookingStatusBadge } from "@/features/bookings/booking-status-badge";
import { formatDateRange, formatMoney } from "@/lib/formatters";
import type { BookingSummaryViewModel } from "@/features/bookings/types";

type BookingSummaryCardProps = { booking: BookingSummaryViewModel; };

const NEXT_ACTION: Record<string, { label: string; variant: string }> = {
  HELD: { label: "Thanh toan ngay", variant: "primary" },
  PENDING_HOST_APPROVAL: { label: "Cho chu xe duyet", variant: "secondary" },
  CONFIRMED: { label: "Xem chi tiet nhan xe", variant: "secondary" },
  CANCELLED: { label: "Xem chi tiet huy", variant: "secondary" },
  COMPLETED: { label: "Xem lai don", variant: "secondary" },
  IN_PROGRESS: { label: "Xem chi tiet", variant: "secondary" },
  REJECTED: { label: "Xem chi tiet", variant: "secondary" },
  EXPIRED: { label: "Xem chi tiet", variant: "secondary" },
};

export function BookingSummaryCard({ booking }: BookingSummaryCardProps) {
  const nextAction = NEXT_ACTION[booking.status] ?? { label: "Xem chi tiet", variant: "secondary" };
  return (
    <article className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 flex-1 space-y-1">
          <h3 className="text-base font-bold text-foreground truncate">{booking.listingTitle}</h3>
          <p className="text-sm text-muted-foreground">{formatDateRange(booking.pickupDate, booking.returnDate)}</p>
          <p className="text-sm font-semibold text-foreground">{formatMoney(booking.totalAmount, booking.currency)}</p>
          {booking.status === "CANCELLED" && booking.voidRetryRequired ? (
            <p className="text-sm font-medium text-amber-700">Dang xu ly hoan tien hoac void trong nen</p>
          ) : null}
        </div>
        <div className="flex shrink-0 flex-col items-end gap-2">
          <BookingStatusBadge status={booking.status} />
          <Link
            href={`/bookings/${booking.id}`}
            className={`rounded-full px-3 py-1.5 text-xs font-semibold transition-opacity hover:opacity-90 ${nextAction.variant === "primary" ? "bg-primary text-primary-foreground" : "bg-secondary text-secondary-foreground"}`}
          >
            {nextAction.label}
          </Link>
        </div>
      </div>
    </article>
  );
}
