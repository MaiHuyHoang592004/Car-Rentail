"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import { CalendarDays, Clock3, ShieldCheck, XCircle } from "lucide-react";

import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { StatusBadge } from "@/components/rentflow/status-badge";
import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { LocationSummary } from "@/features/bookings/location-summary";
import { PolicySnapshotPanel } from "@/features/bookings/policy-snapshot-panel";
import { PriceSnapshotPanel } from "@/features/bookings/price-snapshot-panel";
import { HostActionDialog } from "@/features/host/components/host-action-dialog";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { approveHostBooking, getHostBookingById, rejectHostBooking } from "@/features/host/bookings/api";
import { ApiError } from "@/lib/api-error";
import { getBookingStatusLabel } from "@/lib/display-labels";
import { formatDateRange, formatDateTime, formatMoney } from "@/lib/formatters";
import { newIdempotencyKey } from "@/lib/idempotency";

type HostBookingDetailPageViewProps = {
  bookingId: string;
};

function getRemainingLabel(expiresAt?: string): string | null {
  if (!expiresAt) return null;
  const remaining = Math.floor((Date.parse(expiresAt) - Date.now()) / 1000);
  if (remaining <= 0) return "Da het han";
  const minutes = Math.floor(remaining / 60);
  const seconds = remaining % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

export function HostBookingDetailPageView({ bookingId }: HostBookingDetailPageViewProps) {
  const queryClient = useQueryClient();
  const [rejectOpen, setRejectOpen] = useState(false);

  const bookingQuery = useQuery({
    queryKey: ["host", "bookings", bookingId],
    queryFn: () => getHostBookingById(bookingId),
  });

  const approveMutation = useMutation({
    mutationFn: () => approveHostBooking(bookingId, newIdempotencyKey()),
    onSuccess: (booking) => {
      toast.success("Da duyet booking.");
      queryClient.setQueryData(["host", "bookings", bookingId], booking);
      queryClient.invalidateQueries({ queryKey: ["host", "bookings"] });
    },
    onError: (error: unknown) => {
      toast.error(error instanceof ApiError ? error.message : "Khong the duyet booking.");
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => rejectHostBooking(bookingId, reason, newIdempotencyKey()),
    onSuccess: (booking) => {
      toast.success("Da tu choi booking.");
      setRejectOpen(false);
      queryClient.setQueryData(["host", "bookings", bookingId], booking);
      queryClient.invalidateQueries({ queryKey: ["host", "bookings"] });
    },
    onError: (error: unknown) => {
      toast.error(error instanceof ApiError ? error.message : "Khong the tu choi booking.");
    },
  });

  const booking = bookingQuery.data;
  const pendingApproval = booking?.status === "PENDING_HOST_APPROVAL";
  const remaining = useMemo(
    () => getRemainingLabel(booking?.hostApprovalExpiresAt),
    [booking?.hostApprovalExpiresAt],
  );

  if (bookingQuery.isLoading) {
    return (
      <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/bookings">
        <section className="rf-section-card border-dashed p-10 text-center">
          <p className="text-sm text-muted-foreground">Dang tai chi tiet booking...</p>
        </section>
      </WorkspaceSidebar>
    );
  }

  if (bookingQuery.isError) {
    return (
      <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/bookings">
        <ApiErrorPanel error={bookingQuery.error instanceof ApiError ? bookingQuery.error : undefined} />
      </WorkspaceSidebar>
    );
  }

  if (!booking) return null;

  return (
    <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/bookings">
      <div className="space-y-6">
        <section className="rf-section-card p-6 md:p-8">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary/80">
                Host Workspace
              </p>
              <h1 className="mt-2 text-3xl font-bold text-foreground">Chi tiet booking</h1>
              <p className="mt-2 text-sm text-muted-foreground">{booking.listingTitle}</p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Link
                href="/host/bookings"
                className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
              >
                Quay lai
              </Link>
              <Link
                href={`/host/listings/${booking.listingId}`}
                className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
              >
                Xem listing
              </Link>
            </div>
          </div>
        </section>

        <section className="rf-section-card p-5 md:p-6">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge status={booking.status} label={getBookingStatusLabel(booking.status)} />
                <span className="text-sm font-semibold text-foreground">
                  {formatMoney(booking.totalAmount, booking.currency)}
                </span>
              </div>
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <CalendarDays className="h-4 w-4" />
                {formatDateRange(booking.pickupDate, booking.returnDate)}
              </div>
              {pendingApproval && booking.hostApprovalExpiresAt ? (
                <div className="inline-flex items-center gap-2 rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-900">
                  <Clock3 className="h-3.5 w-3.5" />
                  Con lai {remaining} · het han luc {formatDateTime(booking.hostApprovalExpiresAt)}
                </div>
              ) : null}
            </div>

            {pendingApproval ? (
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  disabled={approveMutation.isPending}
                  onClick={() => approveMutation.mutate()}
                  className="inline-flex items-center gap-2 rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                >
                  <ShieldCheck className="h-4 w-4" />
                  {approveMutation.isPending ? "Dang duyet..." : "Duyet booking"}
                </button>
                <button
                  type="button"
                  disabled={rejectMutation.isPending}
                  onClick={() => setRejectOpen(true)}
                  className="inline-flex items-center gap-2 rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
                >
                  <XCircle className="h-4 w-4" />
                  {rejectMutation.isPending ? "Dang tu choi..." : "Tu choi booking"}
                </button>
              </div>
            ) : null}
          </div>

          {booking.status === "REJECTED" && booking.rejectionReason ? (
            <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-900">
              <p className="font-semibold">Ly do tu choi</p>
              <p className="mt-1">{booking.rejectionReason}</p>
            </div>
          ) : null}
        </section>

        <LocationSummary
          pickupLocation={booking.pickupLocation}
          returnLocation={booking.returnLocation}
        />

        <div className="grid gap-4 lg:grid-cols-2">
          <PriceSnapshotPanel priceSnapshot={booking.priceSnapshot} />
          <PolicySnapshotPanel policySnapshot={booking.policySnapshot} />
        </div>
      </div>

      <HostActionDialog
        open={rejectOpen}
        title="Tu choi booking"
        description="Ly do tu choi se duoc luu va hien thi cho khach thue."
        confirmLabel={rejectMutation.isPending ? "Dang tu choi..." : "Xac nhan tu choi"}
        tone="danger"
        reasonRequired
        reasonPlaceholder="Nhap ly do tu choi booking"
        onClose={() => setRejectOpen(false)}
        onConfirm={(reason) => {
          if (!reason) return;
          rejectMutation.mutate(reason);
        }}
      />
    </WorkspaceSidebar>
  );
}
