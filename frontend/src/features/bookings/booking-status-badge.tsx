import { StatusBadge } from "@/components/rentflow/status-badge";
import { getBookingStatusLabel } from "@/lib/display-labels";
import type { BookingStatus } from "@/features/bookings/types";

type BookingStatusBadgeProps = {
  status: BookingStatus;
};

export function BookingStatusBadge({ status }: BookingStatusBadgeProps) {
  return <StatusBadge status={status} label={getBookingStatusLabel(status)} />;
}
