"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";

import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import {
  cancelBooking,
  getBookingById,
  patchBookingLocations,
  type PatchBookingLocationsInput,
} from "@/features/bookings/api";
import { BookingStatusBadge } from "@/features/bookings/booking-status-badge";
import { CancelBookingDialog } from "@/features/bookings/cancel-booking-dialog";
import { EditLocationsDialog } from "@/features/bookings/edit-locations-dialog";
import type { CancelBookingFormState } from "@/features/bookings/forms";
import { HoldCountdown } from "@/features/bookings/hold-countdown";
import { LocationSummary } from "@/features/bookings/location-summary";
import { PolicySnapshotPanel } from "@/features/bookings/policy-snapshot-panel";
import { PriceSnapshotPanel } from "@/features/bookings/price-snapshot-panel";
import type { BookingStatus } from "@/features/bookings/types";
import { ApiError } from "@/lib/api-error";
import { newIdempotencyKey } from "@/lib/idempotency";

const LOCATION_EDITABLE_STATUSES: BookingStatus[] = [
  "HELD",
  "PENDING_HOST_APPROVAL",
  "CONFIRMED",
];

const PAY_NOW_VISIBLE_STATUSES: BookingStatus[] = [
  "HELD",
  "PENDING_HOST_APPROVAL",
  "CONFIRMED",
];

const MAX_EXPIRE_RETRIES = 3;
const EXPIRE_RETRY_DELAY_MS = 5000;

function getTodayDateString() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatPaymentRetryState(state?: string) {
  if (!state) {
    return "Hệ thống đang tiếp tục xử lý hoàn tiền hoặc void trong nền.";
  }
  if (state === "VOID_RETRY_REQUIRED") {
    return "Hệ thống đang retry thao tác void thanh toán trong nền.";
  }
  return `Trạng thái xử lý thanh toán hiện tại: ${state}.`;
}

type BookingDetailPageViewProps = {
  bookingId: string;
};

export function BookingDetailPageView({ bookingId }: BookingDetailPageViewProps) {
  const queryClient = useQueryClient();
  const [editOpen, setEditOpen] = useState<boolean>(false);
  const [cancelOpen, setCancelOpen] = useState<boolean>(false);
  const cancelKeyRef = useRef<string | null>(null);
  const expireRetryRef = useRef<number>(0);

  const detailQuery = useQuery({
    queryKey: ["bookings", bookingId],
    queryFn: () => getBookingById(bookingId),
  });

  const patchMutation = useMutation({
    mutationFn: (input: PatchBookingLocationsInput) => patchBookingLocations(bookingId, input),
    onSuccess: () => {
      toast.success("Đã cập nhật địa điểm");
      queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
    },
    onError: (error: unknown) => {
      const message =
        error instanceof ApiError ? error.message : "Cập nhật địa điểm thất bại";
      toast.error(message);
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (input: { reason?: string; idempotencyKey: string }) =>
      cancelBooking(bookingId, { reason: input.reason }, input.idempotencyKey),
    onSuccess: (result) => {
      if (result.voidRetryRequired) {
        toast.success("Đã hủy booking; hoàn tiền hoặc void sẽ được xử lý tiếp trong nền");
      } else {
        toast.success("Đã hủy booking");
      }
      queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
      queryClient.invalidateQueries({ queryKey: ["bookings", "me"] });
      cancelKeyRef.current = null;
    },
    onError: (error: unknown) => {
      if (error instanceof ApiError && error.code === "BOOKING_INVALID_STATUS") {
        queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
      }
      const message =
        error instanceof ApiError ? error.message : "Hủy booking thất bại";
      toast.error(message);
    },
  });

  const scheduleExpireRetry = useCallback(() => {
    if (expireRetryRef.current >= MAX_EXPIRE_RETRIES) {
      toast.message("Hold đang được xử lý, refresh trang");
      return;
    }
    expireRetryRef.current += 1;
    window.setTimeout(() => {
      queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
    }, EXPIRE_RETRY_DELAY_MS);
  }, [bookingId, queryClient]);

  const handleHoldExpired = useCallback(() => {
    expireRetryRef.current = 0;
    queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
    scheduleExpireRetry();
  }, [bookingId, queryClient, scheduleExpireRetry]);

  const bookingStatus = detailQuery.data?.status;
  useEffect(() => {
    if (bookingStatus && bookingStatus !== "HELD") {
      expireRetryRef.current = 0;
    }
  }, [bookingStatus]);

  if (detailQuery.isLoading) {
    return (
      <AppShell activePath="/me/bookings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">Đang tải booking...</p>
        </section>
      </AppShell>
    );
  }

  if (detailQuery.isError) {
    const apiErr = detailQuery.error instanceof ApiError ? detailQuery.error : undefined;
    return (
      <AppShell activePath="/me/bookings">
        <ApiErrorPanel error={apiErr} />
        <div className="mt-4">
          <Link
            href="/me/bookings"
            className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Quay về danh sách
          </Link>
        </div>
      </AppShell>
    );
  }

  const booking = detailQuery.data;
  if (!booking) {
    return null;
  }

  const canEditLocations = LOCATION_EDITABLE_STATUSES.includes(booking.status);
  const canCancel =
    booking.status === "HELD" ||
    booking.status === "PENDING_HOST_APPROVAL" ||
    (booking.status === "CONFIRMED" && booking.pickupDate > getTodayDateString());
  const showPayNow = PAY_NOW_VISIBLE_STATUSES.includes(booking.status);
  const cancelHint =
    booking.status === "CONFIRMED" && !canCancel
      ? "Booking đã đến hoặc qua ngày nhận xe nên không còn hủy được từ giao diện này."
      : booking.status === "PENDING_HOST_APPROVAL"
        ? "Hủy booking này có thể kích hoạt void thanh toán đang chờ xử lý."
        : booking.status === "CONFIRMED"
          ? "Hủy booking này sẽ áp dụng chính sách hiện tại và có thể xử lý thanh toán liên quan."
          : null;
  const cancelDialogStatus =
    canCancel &&
    (booking.status === "HELD" ||
      booking.status === "PENDING_HOST_APPROVAL" ||
      booking.status === "CONFIRMED")
      ? booking.status
      : null;

  function openCancelDialog() {
    if (!cancelKeyRef.current) {
      cancelKeyRef.current = newIdempotencyKey();
    }
    setCancelOpen(true);
  }

  function handleCancelClose() {
    setCancelOpen(false);
  }

  function handleCancelConfirm(next: CancelBookingFormState) {
    const key = cancelKeyRef.current ?? newIdempotencyKey();
    cancelKeyRef.current = key;
    cancelMutation.mutate({ reason: next.reason || undefined, idempotencyKey: key });
  }

  function handleLocationPatch(next: PatchBookingLocationsInput) {
    patchMutation.mutate(next);
  }

  return (
    <AppShell activePath="/me/bookings">
      <div className="space-y-6">
        <PageHeader
          title={`Booking ${booking.id}`}
          description="Chi tiết booking, có thể chỉnh địa điểm hoặc hủy nếu trạng thái cho phép."
          actions={
            <Link
              href="/me/bookings"
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Quay về danh sách
            </Link>
          }
        />

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Listing</p>
              <h2 className="text-xl font-bold text-foreground">{booking.listingTitle}</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                {booking.pickupDate} → {booking.returnDate}
              </p>
            </div>
            <BookingStatusBadge status={booking.status} />
          </div>

          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <div className="rounded-lg border border-border bg-background px-3 py-2">
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Tổng tiền</p>
              <p className="mt-1 text-sm font-semibold text-foreground">
                {booking.totalAmount.toLocaleString("en-US")} {booking.currency}
              </p>
            </div>
            <div className="rounded-lg border border-border bg-background px-3 py-2">
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Lý do hủy</p>
              <p className="mt-1 text-sm font-semibold text-foreground">
                {booking.cancellationReason || "—"}
              </p>
            </div>
          </div>

          {booking.status === "HELD" && booking.holdExpiresAt ? (
            <div className="mt-4">
              <HoldCountdown
                key={booking.holdExpiresAt}
                expiresAt={booking.holdExpiresAt}
                onExpire={handleHoldExpired}
              />
            </div>
          ) : null}

          {booking.status === "CANCELLED" && booking.voidRetryRequired ? (
            <div className="mt-4 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
              <p className="font-semibold">Booking đã được hủy, nhưng thanh toán vẫn đang được xử lý tiếp.</p>
              <p className="mt-1">{formatPaymentRetryState(booking.paymentRetryState)}</p>
            </div>
          ) : null}

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              disabled={!canEditLocations || patchMutation.isPending}
              onClick={() => setEditOpen(true)}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:bg-accent"
            >
              Chỉnh địa điểm
            </button>
            <button
              type="button"
              disabled={!canCancel || cancelMutation.isPending}
              onClick={openCancelDialog}
              title={!canCancel ? cancelHint ?? undefined : undefined}
              className="rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
            >
              Hủy booking
            </button>
            {showPayNow ? (
              <Link
                href={`/bookings/${booking.id}/payment`}
                className="rounded-full bg-secondary px-4 py-2 text-sm font-semibold text-secondary-foreground transition-opacity hover:opacity-90"
              >
                Thanh toán
              </Link>
            ) : null}
          </div>
          {cancelHint ? (
            <p className="mt-3 text-sm text-muted-foreground">{cancelHint}</p>
          ) : null}
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
        status={cancelDialogStatus}
        onClose={handleCancelClose}
        onConfirm={handleCancelConfirm}
      />
    </AppShell>
  );
}
