"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { Clock3, ShieldCheck, XCircle } from "lucide-react";

import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { StatusBadge } from "@/components/rentflow/status-badge";
import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostActionDialog } from "@/features/host/components/host-action-dialog";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { getHostBookings, approveHostBooking, rejectHostBooking } from "@/features/host/bookings/api";
import { getHostListings } from "@/features/host/listings/api";
import type { BookingListFilterValue, BookingSummaryViewModel } from "@/features/bookings/types";
import { ApiError } from "@/lib/api-error";
import { getBookingStatusLabel } from "@/lib/display-labels";
import { formatDateRange, formatDateTime, formatMoney } from "@/lib/formatters";
import { newIdempotencyKey } from "@/lib/idempotency";

const PAGE_SIZE = 20;
const HOST_BOOKING_STATUS_FILTERS: BookingListFilterValue[] = [
  "ALL",
  "PENDING_HOST_APPROVAL",
  "CONFIRMED",
  "IN_PROGRESS",
  "COMPLETED",
  "REJECTED",
  "CANCELLED",
  "EXPIRED",
];

function getRemainingLabel(expiresAt?: string): string | null {
  if (!expiresAt) return null;
  const remaining = Math.floor((Date.parse(expiresAt) - Date.now()) / 1000);
  if (remaining <= 0) return "Da het han";
  const minutes = Math.floor(remaining / 60);
  const seconds = remaining % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

export function HostBookingsPageView() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<BookingListFilterValue>("ALL");
  const [listingFilter, setListingFilter] = useState<string>("ALL");
  const [page, setPage] = useState(0);
  const [rejectTarget, setRejectTarget] = useState<BookingSummaryViewModel | null>(null);

  const listingsQuery = useQuery({
    queryKey: ["host", "listings", "options"],
    queryFn: async () => (await getHostListings(undefined, 0, 100)).listings,
  });

  const bookingsQuery = useQuery({
    queryKey: ["host", "bookings", statusFilter, listingFilter, page],
    queryFn: ({ signal }) =>
      getHostBookings(
        {
          status: statusFilter,
          listingId: listingFilter === "ALL" ? undefined : listingFilter,
          page,
          size: PAGE_SIZE,
        },
        signal,
      ),
    placeholderData: keepPreviousData,
  });

  const approveMutation = useMutation({
    mutationFn: (bookingId: string) => approveHostBooking(bookingId, newIdempotencyKey()),
    onSuccess: () => {
      toast.success("Da duyet booking.");
      queryClient.invalidateQueries({ queryKey: ["host", "bookings"] });
    },
    onError: (error: unknown) => {
      toast.error(error instanceof ApiError ? error.message : "Khong the duyet booking.");
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({ bookingId, reason }: { bookingId: string; reason: string }) =>
      rejectHostBooking(bookingId, reason, newIdempotencyKey()),
    onSuccess: () => {
      toast.success("Da tu choi booking.");
      setRejectTarget(null);
      queryClient.invalidateQueries({ queryKey: ["host", "bookings"] });
    },
    onError: (error: unknown) => {
      toast.error(error instanceof ApiError ? error.message : "Khong the tu choi booking.");
    },
  });

  const rows = bookingsQuery.data?.content ?? [];
  const totalPages = bookingsQuery.data?.totalPages ?? 0;
  const pendingCount = rows.filter((booking) => booking.status === "PENDING_HOST_APPROVAL").length;

  function handleStatusChange(next: BookingListFilterValue) {
    setStatusFilter(next);
    setPage(0);
  }

  function handleListingChange(next: string) {
    setListingFilter(next);
    setPage(0);
  }

  return (
    <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/bookings">
      <div className="space-y-6">
        <section className="rf-section-card p-6 md:p-8">
          <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary/80">
                Host Workspace
              </p>
              <h1 className="mt-2 text-3xl font-bold text-foreground">Booking cho chu xe</h1>
              <p className="mt-2 text-sm text-muted-foreground">
                Duyet, tu choi va theo doi cac booking dang cho xu ly.
              </p>
            </div>
            <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
              Dang co <strong>{pendingCount}</strong> booking cho duyet trong trang hien tai.
            </div>
          </div>
        </section>

        <section className="rf-section-card p-4">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-wrap gap-2">
              {HOST_BOOKING_STATUS_FILTERS.map((status) => {
                const active = status === statusFilter;
                return (
                  <button
                    key={status}
                    type="button"
                    onClick={() => handleStatusChange(status)}
                    className={[
                      "rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors",
                      active
                        ? "border-primary bg-primary text-primary-foreground"
                        : "border-border bg-background text-foreground hover:bg-accent",
                    ].join(" ")}
                  >
                    {status === "ALL" ? "Tat ca" : getBookingStatusLabel(status)}
                  </button>
                );
              })}
            </div>

            <select
              value={listingFilter}
              onChange={(event) => handleListingChange(event.target.value)}
              className="h-10 rounded-full border border-border bg-background px-4 text-sm text-foreground"
            >
              <option value="ALL">Tat ca listing</option>
              {(listingsQuery.data ?? []).map((listing) => (
                <option key={listing.id} value={listing.id}>
                  {listing.title}
                </option>
              ))}
            </select>
          </div>
        </section>

        {bookingsQuery.isError ? (
          <ApiErrorPanel error={bookingsQuery.error instanceof ApiError ? bookingsQuery.error : undefined} />
        ) : null}

        {bookingsQuery.isLoading ? (
          <section className="rf-section-card border-dashed p-10 text-center">
            <p className="text-sm text-muted-foreground">Dang tai danh sach booking...</p>
          </section>
        ) : null}

        {!bookingsQuery.isLoading && rows.length === 0 ? (
          <section className="rf-section-card border-dashed p-10 text-center">
            <h2 className="text-xl font-bold text-foreground">Khong co booking nao</h2>
            <p className="mt-2 text-sm text-muted-foreground">Thu doi filter hoac kiem tra listing khac.</p>
          </section>
        ) : null}

        {rows.length > 0 ? (
          <div className="space-y-3">
            {rows.map((booking) => {
              const remaining = getRemainingLabel(booking.hostApprovalExpiresAt);
              return (
                <article key={booking.id} className="rf-section-card p-5">
                  <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                    <div className="min-w-0 flex-1 space-y-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="truncate text-lg font-bold text-foreground">{booking.listingTitle}</h3>
                        <StatusBadge status={booking.status} label={getBookingStatusLabel(booking.status)} />
                      </div>
                      <p className="text-sm text-muted-foreground">
                        {formatDateRange(booking.pickupDate, booking.returnDate)}
                      </p>
                      <p className="text-sm font-semibold text-foreground">
                        {formatMoney(booking.totalAmount, booking.currency)}
                      </p>
                      {booking.status === "PENDING_HOST_APPROVAL" && booking.hostApprovalExpiresAt ? (
                        <div className="inline-flex items-center gap-2 rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-900">
                          <Clock3 className="h-3.5 w-3.5" />
                          Con lai {remaining} · het han luc {formatDateTime(booking.hostApprovalExpiresAt)}
                        </div>
                      ) : null}
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      <Link
                        href={`/host/bookings/${booking.id}`}
                        className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
                      >
                        Xem chi tiet
                      </Link>
                      {booking.status === "PENDING_HOST_APPROVAL" ? (
                        <>
                          <button
                            type="button"
                            disabled={approveMutation.isPending}
                            onClick={() => approveMutation.mutate(booking.id)}
                            className="inline-flex items-center gap-2 rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                          >
                            <ShieldCheck className="h-4 w-4" />
                            Duyet
                          </button>
                          <button
                            type="button"
                            disabled={rejectMutation.isPending}
                            onClick={() => setRejectTarget(booking)}
                            className="inline-flex items-center gap-2 rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
                          >
                            <XCircle className="h-4 w-4" />
                            Tu choi
                          </button>
                        </>
                      ) : null}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        ) : null}

        {totalPages > 1 ? (
          <div className="flex items-center justify-between rounded-2xl border border-border/70 bg-card px-4 py-4 text-sm">
            <button
              type="button"
              disabled={page === 0 || bookingsQuery.isFetching}
              onClick={() => setPage((current) => Math.max(0, current - 1))}
              className="rounded-full border border-border bg-background px-4 py-1.5 font-semibold disabled:opacity-50 hover:enabled:bg-accent"
            >
              Trang truoc
            </button>
            <span className="text-muted-foreground">Trang {page + 1} / {totalPages}</span>
            <button
              type="button"
              disabled={page >= totalPages - 1 || bookingsQuery.isFetching}
              onClick={() => setPage((current) => current + 1)}
              className="rounded-full border border-border bg-background px-4 py-1.5 font-semibold disabled:opacity-50 hover:enabled:bg-accent"
            >
              Trang sau
            </button>
          </div>
        ) : null}
      </div>

      <HostActionDialog
        open={rejectTarget !== null}
        title="Tu choi booking"
        description="Ly do tu choi se duoc hien thi cho khach thue."
        confirmLabel={rejectMutation.isPending ? "Dang tu choi..." : "Xac nhan tu choi"}
        tone="danger"
        reasonRequired
        reasonPlaceholder="Nhap ly do tu choi booking"
        onClose={() => setRejectTarget(null)}
        onConfirm={(reason) => {
          if (!rejectTarget || !reason) return;
          rejectMutation.mutate({ bookingId: rejectTarget.id, reason });
        }}
      />
    </WorkspaceSidebar>
  );
}
