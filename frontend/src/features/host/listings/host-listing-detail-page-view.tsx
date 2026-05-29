"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { ArrowLeft, CalendarDays, Save, Trash2 } from "lucide-react";

import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { StatusBadge } from "@/components/rentflow/status-badge";
import { listingFormSchema, type HostListingFormState } from "@/features/host/forms";
import { HostActionDialog } from "@/features/host/components/host-action-dialog";
import { ListingFormFields } from "@/features/host/listings/listing-form-fields";
import {
  archiveListingSafe,
  getHostListingById,
  reactivateListingSafe,
  submitListingSafe,
  ListingTransitionError,
} from "@/features/host/listings/api";
import type { HostListingViewModel } from "@/features/host/types";
import { getListingStatusLabel } from "@/lib/display-labels";
import { formatMoney } from "@/lib/formatters";

type HostListingDetailPageViewProps = {
  listingId: string;
};

function buildFormFromListing(listing: HostListingViewModel): HostListingFormState {
  return {
    vehicleId: listing.vehicleId,
    title: listing.title,
    description: listing.description,
    city: listing.city,
    address: listing.address,
    basePricePerDay: String(listing.basePricePerDay),
    dailyKmLimit: String(listing.dailyKmLimit),
    instantBook: listing.instantBook,
    cancellationPolicy: listing.cancellationPolicy,
  };
}

export function HostListingDetailPageView({ listingId }: HostListingDetailPageViewProps) {
  const queryClient = useQueryClient();

  const {
    data: listing,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ["host", "listings", listingId],
    queryFn: () => getHostListingById(listingId),
    retry: false,
  });

  const form = useForm<HostListingFormState>({
    resolver: zodResolver(listingFormSchema),
  });
  const [banner, setBanner] = useState<{ type: "success" | "error"; message: string } | null>(null);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [reactivateOpen, setReactivateOpen] = useState(false);

  useEffect(() => {
    if (listing) {
      form.reset(buildFormFromListing(listing));
    }
  }, [form, listing]);

  const submitMutation = useMutation({
    mutationFn: () => submitListingSafe(listingId),
    onSuccess: (updated) => {
      queryClient.setQueryData(["host", "listings", listingId], updated);
      setBanner({ type: "success", message: "Da gui yeu cau duyet thanh cong." });
    },
    onError: (err: ListingTransitionError) => {
      setBanner({ type: "error", message: err.message || "Loi khi gui duyet." });
    },
  });

  const archiveMutation = useMutation({
    mutationFn: () => archiveListingSafe(listingId),
    onSuccess: (updated) => {
      queryClient.setQueryData(["host", "listings", listingId], updated);
      setBanner({ type: "success", message: "Da luu kho tin dang thanh cong." });
      setArchiveOpen(false);
    },
    onError: (err: ListingTransitionError) => {
      setBanner({ type: "error", message: err.message || "Loi khi luu kho." });
      setArchiveOpen(false);
    },
  });

  const reactivateMutation = useMutation({
    mutationFn: () => reactivateListingSafe(listingId),
    onSuccess: (updated) => {
      queryClient.setQueryData(["host", "listings", listingId], updated);
      setBanner({ type: "success", message: "Da kich hoat lai tin dang." });
      setReactivateOpen(false);
    },
    onError: (err: ListingTransitionError) => {
      setBanner({ type: "error", message: err.message || "Loi khi kich hoat." });
      setReactivateOpen(false);
    },
  });

  if (isLoading) {
    return (
      <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/listings">
        <div className="flex items-center justify-center p-20">
          <p className="text-sm text-muted-foreground">Dang tai thong tin tin dang...</p>
        </div>
      </WorkspaceSidebar>
    );
  }

  if (isError || !listing) {
    return (
      <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-2xl font-bold text-foreground">Khong tim thay tin dang</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Tin dang nay khong ton tai hoac ban khong co quyen truy cap.
          </p>
          <Link
            href="/host/listings"
            className="mt-4 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Quay lai danh sach
          </Link>
        </section>
      </WorkspaceSidebar>
    );
  }

  const currentListing = queryClient.getQueryData<HostListingViewModel>(["host", "listings", listingId]) ?? listing;
  const canEdit = currentListing.status === "DRAFT";
  const canSubmit = currentListing.status === "DRAFT";
  const canArchive = ["DRAFT", "PENDING_APPROVAL", "ACTIVE", "SUSPENDED"].includes(currentListing.status);
  const canReactivate = currentListing.status === "ARCHIVED";

  function handleSave() {
    if (!canEdit) {
      return;
    }
    setBanner({ type: "success", message: "Chinh sua tin dang se duoc bo sung o phien ban sau." });
  }

  return (
    <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/listings">
      <div className="space-y-5">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Link
              href="/host/listings"
              className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <ArrowLeft className="h-4 w-4" />
              Quay lai
            </Link>
            <span className="text-muted-foreground">|</span>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold text-foreground truncate max-w-sm">{currentListing.title}</h1>
              <StatusBadge
                status={currentListing.status}
                label={getListingStatusLabel(currentListing.status)}
              />
            </div>
          </div>
          <Link
            href={`/host/listings/${listingId}/availability`}
            className="flex items-center gap-1.5 rounded-full border border-border bg-background px-3 py-1.5 text-sm font-semibold text-foreground hover:bg-accent"
          >
            <CalendarDays className="h-4 w-4" />
            Lich xe
          </Link>
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

        {/* Listing card */}
        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          {/* Price summary */}
          <div className="mb-4 flex items-center justify-between rounded-lg border border-border bg-background p-3">
            <div>
              <p className="text-xs text-muted-foreground">Gia / ngay</p>
              <p className="text-lg font-bold text-foreground">
                {formatMoney(currentListing.basePricePerDay, currentListing.currency)}
              </p>
            </div>
            <div className="text-right">
              <p className="text-xs text-muted-foreground">{currentListing.city}</p>
              <p className="text-sm text-foreground">{currentListing.vehicleLabel}</p>
            </div>
          </div>

          {/* Action bar */}
          <div className="flex flex-wrap gap-2">
            {canSubmit ? (
              <button
                type="button"
                disabled={submitMutation.isPending}
                onClick={() => submitMutation.mutate()}
                className="flex items-center gap-2 rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
              >
                {submitMutation.isPending ? "Dang gui..." : "Gui duyet"}
              </button>
            ) : null}
            <button
              type="button"
              disabled={!canArchive || archiveMutation.isPending}
              onClick={() => setArchiveOpen(true)}
              className="flex items-center gap-2 rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
            >
              <Trash2 className="h-4 w-4" />
              {archiveMutation.isPending ? "Dang luu kho..." : "Luu kho"}
            </button>
            {canReactivate ? (
              <button
                type="button"
                disabled={reactivateMutation.isPending}
                onClick={() => setReactivateOpen(true)}
                className="flex items-center gap-2 rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
              >
                {reactivateMutation.isPending ? "Dang kich hoat..." : "Kich hoat lai"}
              </button>
            ) : null}
          </div>
        </section>

        {/* Form */}
        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <form onSubmit={form.handleSubmit(handleSave)} noValidate className="space-y-6">
            <ListingFormFields
              register={form.register}
              errors={form.formState.errors}
              setValue={form.setValue}
              watch={form.watch}
              vehicleOptions={[]}
              disableVehicleSelect
              readOnly={!canEdit}
              listing={currentListing}
            />

            {canEdit ? (
              <div className="flex flex-wrap gap-2">
                <button
                  type="submit"
                  className="flex items-center gap-2 rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
                >
                  <Save className="h-4 w-4" />
                  Luu thay doi
                </button>
              </div>
            ) : (
              <div className="rounded-lg border border-dashed border-border bg-muted/50 px-4 py-3 text-sm text-muted-foreground">
                Chinh sua tin dang se duoc bo sung o phien ban sau.
              </div>
            )}
          </form>
        </section>
      </div>

      <HostActionDialog
        open={archiveOpen}
        title="Luu kho tin dang"
        description="Tin dang se bi chuyen sang trang thai luu kho. No se khong con hien thi voi khach thue."
        confirmLabel="Xac nhan luu kho"
        tone="danger"
        onClose={() => setArchiveOpen(false)}
        onConfirm={() => archiveMutation.mutate()}
      />

      <HostActionDialog
        open={reactivateOpen}
        title="Kich hoat lai tin dang"
        description="Tin dang da luu kho se duoc dat lai ve trang thai nhap, cho phep ban chinh sua va gui duyet lai."
        confirmLabel="Kich hoat"
        onClose={() => setReactivateOpen(false)}
        onConfirm={() => reactivateMutation.mutate()}
      />
    </WorkspaceSidebar>
  );
}