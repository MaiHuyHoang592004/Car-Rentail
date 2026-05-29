"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import {
  adminApproveListingSafe,
  adminGetListingDetail,
  adminReactivateListingSafe,
  adminRejectListingSafe,
  adminSuspendListingSafe,
  AdminListingActionError,
} from "@/features/admin/listings/api";
import type { AdminListingDetail } from "@/features/admin/listings/types";
import { ApiError } from "@/lib/api-error";

/* ------------------------------------------------------------------ */
/*  Helpers                                                           */
/* ------------------------------------------------------------------ */

function statusBadgeClass(status: string): string {
  switch (status) {
    case "PENDING_APPROVAL":
      return "bg-amber-100 text-amber-800 border-amber-200";
    case "ACTIVE":
      return "bg-emerald-100 text-emerald-800 border-emerald-200";
    case "SUSPENDED":
      return "bg-rose-100 text-rose-800 border-rose-200";
    case "DRAFT":
      return "bg-slate-100 text-slate-800 border-slate-200";
    case "ARCHIVED":
      return "bg-gray-100 text-gray-600 border-gray-200";
    default:
      return "bg-slate-100 text-slate-800 border-slate-200";
  }
}

/* ------------------------------------------------------------------ */
/*  Reason Dialog                                                     */
/* ------------------------------------------------------------------ */

type ReasonDialogProps = {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  tone?: "default" | "danger";
  onClose: () => void;
  onConfirm: (reason: string) => void;
};

function ReasonDialog({
  open,
  title,
  description,
  confirmLabel,
  tone = "default",
  onClose,
  onConfirm,
}: ReasonDialogProps) {
  const [reason, setReason] = useState("");

  if (!open) return null;

  const confirmClass =
    tone === "danger"
      ? "rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white hover:opacity-90"
      : "rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90";

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/45 p-4">
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-md rounded-xl border border-border bg-card p-5 shadow-lg"
      >
        <h3 className="text-lg font-bold text-foreground">{title}</h3>
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          placeholder="Nhập lý do (tùy chọn)"
          className="mt-4 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary"
        />
        <div className="mt-4 flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={() => onConfirm(reason)}
            className={confirmClass}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Confirm Dialog                                                    */
/* ------------------------------------------------------------------ */

type ConfirmDialogProps = {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  tone?: "default" | "danger";
  onClose: () => void;
  onConfirm: () => void;
};

function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  tone = "default",
  onClose,
  onConfirm,
}: ConfirmDialogProps) {
  if (!open) return null;

  const confirmClass =
    tone === "danger"
      ? "rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white hover:opacity-90"
      : "rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90";

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/45 p-4">
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-md rounded-xl border border-border bg-card p-5 shadow-lg"
      >
        <h3 className="text-lg font-bold text-foreground">{title}</h3>
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
        <div className="mt-5 flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
          >
            Hủy
          </button>
          <button type="button" onClick={onConfirm} className={confirmClass}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Page View                                                         */
/* ------------------------------------------------------------------ */

type AdminListingDetailPageViewProps = {
  listingId: string;
};

export function AdminListingDetailPageView({ listingId }: AdminListingDetailPageViewProps) {
  const queryClient = useQueryClient();
  const [rejectOpen, setRejectOpen] = useState(false);
  const [suspendOpen, setSuspendOpen] = useState(false);
  const [approveOpen, setApproveOpen] = useState(false);
  const [reactivateOpen, setReactivateOpen] = useState(false);

  const { data: detail, isLoading, isError } = useQuery({
    queryKey: ["admin", "listings", listingId],
    queryFn: () => adminGetListingDetail(listingId),
    retry: false,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["admin", "listings", listingId] });
    queryClient.invalidateQueries({ queryKey: ["admin", "listings"] });
  };

  const approveMutation = useMutation({
    mutationFn: () => adminApproveListingSafe(listingId),
    onSuccess: () => {
      toast.success("Đã duyệt tin đăng");
      invalidate();
      setApproveOpen(false);
    },
    onError: (err: unknown) => {
      const msg =
        err instanceof AdminListingActionError
          ? err.message
          : err instanceof ApiError
            ? err.message
            : "Duyệt tin đăng thất bại";
      toast.error(msg);
      setApproveOpen(false);
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => adminRejectListingSafe(listingId, reason),
    onSuccess: () => {
      toast.success("Đã từ chối tin đăng");
      invalidate();
      setRejectOpen(false);
    },
    onError: (err: unknown) => {
      const msg =
        err instanceof AdminListingActionError
          ? err.message
          : err instanceof ApiError
            ? err.message
            : "Từ chối tin đăng thất bại";
      toast.error(msg);
      setRejectOpen(false);
    },
  });

  const suspendMutation = useMutation({
    mutationFn: (reason: string) => adminSuspendListingSafe(listingId, reason),
    onSuccess: () => {
      toast.success("Đã tạm ngưng tin đăng");
      invalidate();
      setSuspendOpen(false);
    },
    onError: (err: unknown) => {
      const msg =
        err instanceof AdminListingActionError
          ? err.message
          : err instanceof ApiError
            ? err.message
            : "Tạm ngưng tin đăng thất bại";
      toast.error(msg);
      setSuspendOpen(false);
    },
  });

  const reactivateMutation = useMutation({
    mutationFn: () => adminReactivateListingSafe(listingId),
    onSuccess: () => {
      toast.success("Đã kích hoạt lại tin đăng");
      invalidate();
      setReactivateOpen(false);
    },
    onError: (err: unknown) => {
      const msg =
        err instanceof AdminListingActionError
          ? err.message
          : err instanceof ApiError
            ? err.message
            : "Kích hoạt lại tin đăng thất bại";
      toast.error(msg);
      setReactivateOpen(false);
    },
  });

  if (isLoading) {
    return (
      <AppShell activePath="/admin/listings">
        <PageSkeleton message="Đang tải chi tiết..." />
      </AppShell>
    );
  }

  if (isError || !detail) {
    return (
      <AppShell activePath="/admin/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">
            Không tìm thấy tin đăng hoặc bạn không có quyền truy cập.
          </p>
          <Link
            href="/admin/listings"
            className="mt-4 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90"
          >
            Quay về danh sách
          </Link>
        </section>
      </AppShell>
    );
  }

  const { listing, host, bookingSummary } = detail;
  const status = listing.status;

  // Which actions are available for current status
  const canApprove = status === "PENDING_APPROVAL";
  const canReject = status === "PENDING_APPROVAL";
  const canSuspend = status === "ACTIVE";
  const canReactivate = status === "SUSPENDED";

  const isLoadingAction =
    approveMutation.isPending ||
    rejectMutation.isPending ||
    suspendMutation.isPending ||
    reactivateMutation.isPending;

  return (
    <AppShell activePath="/admin/listings">
      <div className="space-y-6">
        <PageHeader
          title={listing.title}
          description={`Chi tiết listing · ${listing.city}`}
          actions={
            <Link
              href="/admin/listings"
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Quay về danh sách
            </Link>
          }
        />

        {/* Listing info card */}
        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span
              className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-[11px] font-semibold ${statusBadgeClass(status)}`}
            >
              {status}
            </span>
            <span className="text-xs text-muted-foreground">{listing.city}</span>
          </div>

          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <InfoBlock label="Giá/ngày">
              {listing.basePricePerDay.toLocaleString("en-US")} {listing.currency}
            </InfoBlock>
            <InfoBlock label="Giới hạn km/ngày">{listing.dailyKmLimit}</InfoBlock>
            <InfoBlock label="Instant Book">
              {listing.instantBook ? "Có" : "Không"}
            </InfoBlock>
            <InfoBlock label="Chính sách hủy">{listing.cancellationPolicy}</InfoBlock>
          </div>

          {listing.description && (
            <p className="mt-4 text-sm text-muted-foreground whitespace-pre-line">
              {listing.description}
            </p>
          )}
        </section>

        {/* Host + booking summary */}
        <section className="grid gap-4 sm:grid-cols-2">
          <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">Chủ xe</p>
            <p className="mt-1 text-sm font-semibold text-foreground">
              {host?.fullName ?? "Không rõ"}
            </p>
            <p className="text-xs text-muted-foreground">{host?.email ?? ""}</p>
          </div>
          <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">
              Đang active bookings
            </p>
            <p className="mt-1 text-2xl font-bold text-foreground">
              {bookingSummary.activeBookings}
            </p>
          </div>
        </section>

        {/* Actions */}
        <section className="flex flex-wrap gap-2">
          {canApprove && (
            <button
              type="button"
              disabled={isLoadingAction}
              onClick={() => setApproveOpen(true)}
              className="rounded-full bg-emerald-600 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
            >
              {approveMutation.isPending ? "Đang duyệt..." : "Duyệt"}
            </button>
          )}
          {canReject && (
            <button
              type="button"
              disabled={isLoadingAction}
              onClick={() => setRejectOpen(true)}
              className="rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
            >
              {rejectMutation.isPending ? "Đang từ chối..." : "Từ chối"}
            </button>
          )}
          {canSuspend && (
            <button
              type="button"
              disabled={isLoadingAction}
              onClick={() => setSuspendOpen(true)}
              className="rounded-full bg-amber-600 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
            >
              {suspendMutation.isPending ? "Đang tạm ngưng..." : "Tạm ngưng"}
            </button>
          )}
          {canReactivate && (
            <button
              type="button"
              disabled={isLoadingAction}
              onClick={() => setReactivateOpen(true)}
              className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
            >
              {reactivateMutation.isPending ? "Đang kích hoạt..." : "Kích hoạt lại"}
            </button>
          )}
        </section>
      </div>

      {/* Dialogs */}
      <ConfirmDialog
        open={approveOpen}
        title="Duyệt listing"
        description="Listing sẽ chuyển sang ACTIVE và hệ thống sẽ tạo availability rows. Bạn chắc chắn?"
        confirmLabel="Duyệt"
        onClose={() => setApproveOpen(false)}
        onConfirm={() => approveMutation.mutate()}
      />
      <ReasonDialog
        open={rejectOpen}
        title="Từ chối listing"
        description="Listing sẽ chuyển về DRAFT. Vui lòng nhập lý do."
        confirmLabel="Từ chối"
        tone="danger"
        onClose={() => setRejectOpen(false)}
        onConfirm={(reason) => rejectMutation.mutate(reason)}
      />
      <ReasonDialog
        open={suspendOpen}
        title="Tạm ngưng listing"
        description="Listing sẽ bị tạm ngưng và ẩn khỏi tìm kiếm công khai."
        confirmLabel="Tạm ngưng"
        tone="danger"
        onClose={() => setSuspendOpen(false)}
        onConfirm={(reason) => suspendMutation.mutate(reason)}
      />
      <ConfirmDialog
        open={reactivateOpen}
        title="Kích hoạt lại listing"
        description="Listing sẽ chuyển về ACTIVE và hiển thị lại trên tìm kiếm công khai."
        confirmLabel="Kích hoạt lại"
        onClose={() => setReactivateOpen(false)}
        onConfirm={() => reactivateMutation.mutate()}
      />
    </AppShell>
  );
}

/* ------------------------------------------------------------------ */
/*  Tiny helper                                                       */
/* ------------------------------------------------------------------ */

function InfoBlock({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-border bg-background px-3 py-2">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-1 text-sm font-semibold text-foreground">{children}</p>
    </div>
  );
}
