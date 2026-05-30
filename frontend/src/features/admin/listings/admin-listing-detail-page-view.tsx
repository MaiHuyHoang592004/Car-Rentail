"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { ArrowLeft, Ban, CheckCircle, RefreshCw, ShieldX } from "lucide-react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { StatusBadge } from "@/components/rentflow/status-badge";
import { HostActionDialog } from "@/features/host/components/host-action-dialog";
import {
  adminApproveListingSafe,
  adminGetListingDetail,
  adminReactivateListingSafe,
  adminRejectListingSafe,
  adminSuspendListingSafe,
  AdminListingActionError,
} from "@/features/admin/listings/api";
import { getListingStatusLabel } from "@/lib/display-labels";
import { getCancellationPolicyLabel } from "@/lib/display-labels";
import { formatMoney } from "@/lib/formatters";
import { ApiError } from "@/lib/api-error";
import type { AdminListingDetail } from "@/features/admin/listings/types";

type AdminListingDetailPageViewProps = {
  listingId: string;
};

function DetailInfoBlock({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-border bg-background px-3 py-2">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-1 text-sm font-semibold text-foreground">{children}</p>
    </div>
  );
}

export function AdminListingDetailPageView({ listingId }: AdminListingDetailPageViewProps) {
  const queryClient = useQueryClient();
  const [rejectOpen, setRejectOpen] = useState(false);
  const [suspendOpen, setSuspendOpen] = useState(false);
  const [approveOpen, setApproveOpen] = useState(false);
  const [reactivateOpen, setReactivateOpen] = useState(false);
  const [banner, setBanner] = useState<{ type: "success" | "error"; message: string } | null>(null);

  const { data: detail, isLoading, isError } = useQuery({
    queryKey: ["admin", "listings", listingId],
    queryFn: () => adminGetListingDetail(listingId),
    retry: false,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["admin", "listings", listingId] });
    queryClient.invalidateQueries({ queryKey: ["admin", "listings"] });
  };

  function showError(err: unknown, fallback: string) {
    const msg =
      err instanceof AdminListingActionError
        ? err.message
        : err instanceof ApiError
          ? err.message
          : fallback;
    toast.error(msg);
  }

  const approveMutation = useMutation({
    mutationFn: () => adminApproveListingSafe(listingId),
    onSuccess: () => {
      toast.success("Da duyet tin dang thanh cong.");
      invalidate();
      setApproveOpen(false);
      setBanner({ type: "success", message: "Tin dang da duyet." });
    },
    onError: (err) => { showError(err, "Duyet tin dang that bai."); setApproveOpen(false); },
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => adminRejectListingSafe(listingId, reason),
    onSuccess: () => {
      toast.success("Da tu choi tin dang.");
      invalidate();
      setRejectOpen(false);
      setBanner({ type: "success", message: "Tin dang da tu choi." });
    },
    onError: (err) => { showError(err, "Tu choi tin dang that bai."); setRejectOpen(false); },
  });

  const suspendMutation = useMutation({
    mutationFn: (reason: string) => adminSuspendListingSafe(listingId, reason),
    onSuccess: () => {
      toast.success("Da tam ngung tin dang.");
      invalidate();
      setSuspendOpen(false);
      setBanner({ type: "success", message: "Tin dang da tam ngung." });
    },
    onError: (err) => { showError(err, "Tam ngung tin dang that bai."); setSuspendOpen(false); },
  });

  const reactivateMutation = useMutation({
    mutationFn: () => adminReactivateListingSafe(listingId),
    onSuccess: () => {
      toast.success("Da kich hoat lai tin dang.");
      invalidate();
      setReactivateOpen(false);
      setBanner({ type: "success", message: "Tin dang da kich hoat lai." });
    },
    onError: (err) => { showError(err, "Kich hoat lai that bai."); setReactivateOpen(false); },
  });

  if (isLoading) {
    return (
      <AppShell activePath="/admin/listings">
        <PageSkeleton message="Dang tai chi tiet..." />
      </AppShell>
    );
  }

  if (isError || !detail) {
    return (
      <AppShell activePath="/admin/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">
            Khong tim thay tin dang hoac ban khong co quyen truy cap.
          </p>
          <Link
            href="/admin/listings"
            className="mt-4 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Quay ve danh sach
          </Link>
        </section>
      </AppShell>
    );
  }

  const { listing, host, bookingSummary, vehicle, moderation } = detail;
  const status = listing.status;

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
      <div className="space-y-5">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Link
              href="/admin/listings"
              className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <ArrowLeft className="h-4 w-4" />
              Quay lai
            </Link>
            <span className="text-muted-foreground">|</span>
            <h1 className="text-xl font-bold text-foreground truncate max-w-md">
              {listing.title}
            </h1>
            <StatusBadge
              status={status}
              label={getListingStatusLabel(status)}
              className="bg-amber-100 text-amber-800 border-amber-200"
            />
          </div>
        </div>

        {/* Banner */}
        {banner ? (
          <section
            className={`rounded-xl border px-4 py-3 text-sm ${
              banner.type === "success"
                ? "border-emerald-200 bg-emerald-50 text-emerald-900"
                : "border-rose-200 bg-rose-50 text-rose-900"
            }`}
          >
            {banner.message}
          </section>
        ) : null}

        {/* Two-column layout */}
        <div className="flex flex-col gap-5 lg:flex-row lg:items-start">
          {/* Left: listing info */}
          <div className="min-w-0 flex-1 space-y-4">
            {/* Listing summary card */}
            <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <h2 className="text-sm font-bold uppercase tracking-wide text-muted-foreground mb-3">
                Thong tin tin dang
              </h2>
              <div className="grid gap-3 sm:grid-cols-2">
                <DetailInfoBlock label="Gia / ngay">
                  {formatMoney(listing.basePricePerDay, listing.currency)}
                </DetailInfoBlock>
                <DetailInfoBlock label="Gioi han km / ngay">
                  {listing.dailyKmLimit} km
                </DetailInfoBlock>
                <DetailInfoBlock label="Cho phep dat ngay">
                  {listing.instantBook ? "Co" : "Khong"}
                </DetailInfoBlock>
                <DetailInfoBlock label="Chinh sach huy">
                  {getCancellationPolicyLabel(listing.cancellationPolicy)}
                </DetailInfoBlock>
                <DetailInfoBlock label="Thanh pho">
                  {listing.city}
                </DetailInfoBlock>
                <DetailInfoBlock label="Dia chi">
                  {listing.address || "—"}
                </DetailInfoBlock>
              </div>
              {listing.description ? (
                <div className="mt-3 rounded-lg border border-border bg-background px-3 py-2">
                  <p className="text-xs uppercase tracking-wide text-muted-foreground">Mo ta</p>
                  <p className="mt-1 whitespace-pre-line text-sm text-foreground">
                    {listing.description}
                  </p>
                </div>
              ) : null}
            </section>

            {/* Host info */}
            <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <h2 className="text-sm font-bold uppercase tracking-wide text-muted-foreground mb-3">
                Chu xe
              </h2>
              {host ? (
                <div className="space-y-1">
                  <p className="text-sm font-semibold text-foreground">{host.fullName}</p>
                  <p className="text-sm text-muted-foreground">{host.email}</p>
                  <p className="text-xs text-muted-foreground">ID: {host.id}</p>
                  <p className="text-xs text-muted-foreground">
                    Tin dang dang hoat dong: {host.activeListings ?? 0}
                  </p>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">Khong co thong tin chu xe.</p>
              )}
            </section>

            <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <h2 className="text-sm font-bold uppercase tracking-wide text-muted-foreground mb-3">
                Rủi ro xe và moderation
              </h2>
              <div className="grid gap-3 sm:grid-cols-2">
                <DetailInfoBlock label="Trang thai xe">
                  {vehicle?.status ?? "—"}
                </DetailInfoBlock>
                <DetailInfoBlock label="Tin active cung xe">
                  {vehicle?.activeListings ?? 0}
                </DetailInfoBlock>
                <DetailInfoBlock label="Nguon tam ngung">
                  {moderation?.suspensionSource ?? "—"}
                </DetailInfoBlock>
                <DetailInfoBlock label="Tam ngung den">
                  {moderation?.suspensionUntil ? new Date(moderation.suspensionUntil).toLocaleString("vi-VN") : "—"}
                </DetailInfoBlock>
              </div>
              {moderation?.suspensionReason || moderation?.rejectedReason ? (
                <div className="mt-3 rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground">
                  {moderation.suspensionReason ? <p>Tam ngung: {moderation.suspensionReason}</p> : null}
                  {moderation.rejectedReason ? <p>Tu choi: {moderation.rejectedReason}</p> : null}
                </div>
              ) : null}
            </section>

            {/* Booking summary */}
            <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <h2 className="text-sm font-bold uppercase tracking-wide text-muted-foreground mb-3">
                Dat xe hien tai
              </h2>
              <p className="text-2xl font-bold text-foreground">
                {bookingSummary.activeBookings}
              </p>
              <p className="text-xs text-muted-foreground">dat xe dang hoat dong</p>
            </section>
          </div>

          {/* Right: sticky action panel */}
          <div className="w-full lg:sticky lg:top-6 lg:w-72 lg:shrink-0">
            <div className="rounded-xl border border-border bg-card p-5 shadow-sm space-y-3">
              <div>
                <p className="text-xs uppercase tracking-wide text-muted-foreground">Trang thai hien tai</p>
                <div className="mt-1">
                  <StatusBadge
                    status={status}
                    label={getListingStatusLabel(status)}
                    className="bg-amber-100 text-amber-800 border-amber-200"
                  />
                </div>
              </div>

              <div className="border-t border-border pt-3 space-y-2">
                {canApprove && (
                  <button
                    type="button"
                    disabled={isLoadingAction}
                    onClick={() => setApproveOpen(true)}
                    className="flex w-full items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
                  >
                    <CheckCircle className="h-4 w-4" />
                    {approveMutation.isPending ? "Dang duyet..." : "Duyet"}
                  </button>
                )}
                {canReject && (
                  <button
                    type="button"
                    disabled={isLoadingAction}
                    onClick={() => setRejectOpen(true)}
                    className="flex w-full items-center gap-2 rounded-lg bg-rose-600 px-4 py-2.5 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
                  >
                    <ShieldX className="h-4 w-4" />
                    {rejectMutation.isPending ? "Dang tu choi..." : "Tu choi"}
                  </button>
                )}
                {canSuspend && (
                  <button
                    type="button"
                    disabled={isLoadingAction}
                    onClick={() => setSuspendOpen(true)}
                    className="flex w-full items-center gap-2 rounded-lg bg-amber-600 px-4 py-2.5 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
                  >
                    <Ban className="h-4 w-4" />
                    {suspendMutation.isPending ? "Dang tam ngung..." : "Tam ngung"}
                  </button>
                )}
                {canReactivate && (
                  <button
                    type="button"
                    disabled={isLoadingAction}
                    onClick={() => setReactivateOpen(true)}
                    className="flex w-full items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
                  >
                    <RefreshCw className="h-4 w-4" />
                    {reactivateMutation.isPending ? "Dang kich hoat..." : "Kich hoat lai"}
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Dialogs */}
      <HostActionDialog
        open={approveOpen}
        title="Duyet tin dang"
        description="Tin dang se chuyen sang Hoat dong va he thong se tao cac dong availability. Ban chan chan muon duyet?"
        confirmLabel="Duyet"
        onClose={() => setApproveOpen(false)}
        onConfirm={() => approveMutation.mutate()}
      />
      <HostActionDialog
        open={rejectOpen}
        title="Tu choi tin dang"
        description="Tin dang se duoc dat ve trang thai Nhap (Draft). Vui long nhap ly do."
        confirmLabel="Tu choi"
        tone="danger"
        onClose={() => setRejectOpen(false)}
        onConfirm={(reason) => rejectMutation.mutate(reason ?? "")}
      />
      <HostActionDialog
        open={suspendOpen}
        title="Tam ngung tin dang"
        description="Tin dang se bi an khoi tim kiem cong khai. Cac dat xe hien tai van duoc giu nguyen."
        confirmLabel="Tam ngung"
        tone="danger"
        onClose={() => setSuspendOpen(false)}
        onConfirm={(reason) => suspendMutation.mutate(reason ?? "")}
      />
      <HostActionDialog
        open={reactivateOpen}
        title="Kich hoat lai tin dang"
        description="Tin dang se quay tro lai trang thai Cho duyet, cho phep chinh sua va gui duyet lai."
        confirmLabel="Kich hoat lai"
        onClose={() => setReactivateOpen(false)}
        onConfirm={() => reactivateMutation.mutate()}
      />
    </AppShell>
  );
}
