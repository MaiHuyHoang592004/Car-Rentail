import Link from "next/link";

import { BookingStatusBadge } from "@/features/bookings/booking-status-badge";
import type { BookingSummaryViewModel } from "@/features/bookings/types";

type BookingSummaryCardProps = {
  booking: BookingSummaryViewModel;
};

export function BookingSummaryCard({ booking }: BookingSummaryCardProps) {
  return (
    <article className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Booking {booking.id}
          </p>
          <h3 className="text-lg font-bold text-foreground">{booking.listingTitle}</h3>
          <p className="text-sm text-muted-foreground">
            {booking.pickupDate} to {booking.returnDate}
          </p>
          <p className="text-sm font-semibold text-foreground">
            {booking.totalAmount.toLocaleString("en-US")} {booking.currency}
          </p>
          {booking.status === "CANCELLED" && booking.voidRetryRequired ? (
            <p className="text-sm font-medium text-amber-700">
              Đang xử lý hoàn tiền hoặc void trong nền
            </p>
          ) : null}
        </div>

        <div className="flex items-center gap-2">
          <BookingStatusBadge status={booking.status} />
          <Link
            href={`/bookings/${booking.id}`}
            className="rounded-full bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            View detail
          </Link>
        </div>
      </div>
    </article>
  );
}
