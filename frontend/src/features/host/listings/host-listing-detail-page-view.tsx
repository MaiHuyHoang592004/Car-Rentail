"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { ArrowLeft, CalendarDays, Plus, Save, Trash2 } from "lucide-react";

import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { StatusBadge } from "@/components/rentflow/status-badge";
import { listingFormSchema, type HostListingFormState } from "@/features/host/forms";
import { HostActionDialog } from "@/features/host/components/host-action-dialog";
import { ListingFormFields } from "@/features/host/listings/listing-form-fields";
import {
  archiveListingSafe,
  createHostListingExtra,
  deleteHostListingExtra,
  getHostListingById,
  getHostListingExtras,
  reactivateListingSafe,
  resumeListingSafe,
  submitListingSafe,
  ListingTransitionError,
  updateHostListingExtra,
  updateListing,
} from "@/features/host/listings/api";
import type { HostListingExtraViewModel, HostListingViewModel } from "@/features/host/types";
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
  const [extraDraft, setExtraDraft] = useState<{
    name: string;
    pricingType: "PER_DAY" | "PER_TRIP";
    price: string;
  }>({ name: "", pricingType: "PER_DAY", price: "" });

  const { data: extras = [] } = useQuery({
    queryKey: ["host", "listings", listingId, "extras"],
    queryFn: () => getHostListingExtras(listingId),
    enabled: !!listing,
  });

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

  const saveMutation = useMutation({
    mutationFn: (values: HostListingFormState) =>
      updateListing(listingId, {
        title: values.title,
        description: values.description,
        city: values.city,
        address: values.address,
        basePricePerDay: Number(values.basePricePerDay),
        dailyKmLimit: Number(values.dailyKmLimit),
        instantBook: values.instantBook,
        cancellationPolicy: values.cancellationPolicy,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(["host", "listings", listingId], updated);
      setBanner({ type: "success", message: "Da luu thay doi tin dang." });
    },
    onError: (err: ListingTransitionError | Error) => {
      setBanner({ type: "error", message: err.message || "Loi khi luu thay doi." });
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

  const resumeMutation = useMutation({
    mutationFn: () => resumeListingSafe(listingId),
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

  const createExtraMutation = useMutation({
    mutationFn: () =>
      createHostListingExtra(listingId, {
        name: extraDraft.name.trim(),
        pricingType: extraDraft.pricingType,
        price: Number(extraDraft.price),
      }),
    onSuccess: async () => {
      setExtraDraft({ name: "", pricingType: "PER_DAY", price: "" });
      setBanner({ type: "success", message: "Da them extra moi." });
      await queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId, "extras"] });
      await queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId] });
    },
    onError: (err: Error) => {
      setBanner({ type: "error", message: err.message || "Loi khi them extra." });
    },
  });

  const toggleExtraMutation = useMutation({
    mutationFn: ({ extraId, active }: { extraId: string; active: boolean }) =>
      updateHostListingExtra(listingId, extraId, { active }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId, "extras"] });
      await queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId] });
    },
  });

  const deleteExtraMutation = useMutation({
    mutationFn: (extraId: string) => deleteHostListingExtra(listingId, extraId),
    onSuccess: async () => {
      setBanner({ type: "success", message: "Da an extra khoi listing." });
      await queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId, "extras"] });
      await queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId] });
    },
    onError: (err: Error) => {
      setBanner({ type: "error", message: err.message || "Loi khi xoa extra." });
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
  const canReactivateArchived = currentListing.status === "ARCHIVED";
  const canResumeSuspended = currentListing.status === "SUSPENDED";

  function handleSave() {
    form.handleSubmit((values) => saveMutation.mutate(values))();
  }

  function guidanceMessage(): string | null {
    if (currentListing.status === "ACTIVE") {
      return "Listing dang ACTIVE. Neu muon sua noi dung hoac extras, hay luu kho listing, kich hoat lai ve DRAFT roi gui duyet lai.";
    }
    if (currentListing.status === "ARCHIVED") {
      return "Listing da luu kho. Kich hoat lai de dua ve DRAFT truoc khi chinh sua.";
    }
    if (currentListing.status === "PENDING_APPROVAL") {
      return "Listing dang cho duyet nen tam thoi chi doc. Neu can sua, hay luu kho va tao lai flow duyet.";
    }
    if (currentListing.status === "SUSPENDED") {
      return "Listing dang tam ngung. Ban chi co the kich hoat lai neu xe da ACTIVE; muon chinh sua thi can dua ve DRAFT tu flow archive/reactivate.";
    }
    return null;
  }

  function renderExtraRow(extra: HostListingExtraViewModel) {
    return (
      <div
        key={extra.id}
        className="flex flex-col gap-3 rounded-2xl border border-border bg-background px-4 py-4 sm:flex-row sm:items-center sm:justify-between"
      >
        <div>
          <p className="font-semibold text-foreground">{extra.name}</p>
          <p className="text-sm text-muted-foreground">
            {extra.pricingType === "PER_DAY" ? "Tinh theo ngay" : "Tinh theo chuyen"} · {formatMoney(extra.price, "VND")}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span
            className={`rounded-full px-3 py-1 text-xs font-semibold ${
              extra.active ? "bg-emerald-100 text-emerald-900" : "bg-zinc-100 text-zinc-700"
            }`}
          >
            {extra.active ? "Dang bat" : "Da an"}
          </span>
          {canEdit ? (
            <>
              <button
                type="button"
                onClick={() => toggleExtraMutation.mutate({ extraId: extra.id, active: !extra.active })}
                className="rounded-full border border-border px-3 py-1.5 text-xs font-semibold text-foreground hover:bg-accent"
              >
                {extra.active ? "Tat" : "Bat lai"}
              </button>
              <button
                type="button"
                onClick={() => deleteExtraMutation.mutate(extra.id)}
                className="rounded-full border border-rose-200 px-3 py-1.5 text-xs font-semibold text-rose-700 hover:bg-rose-50"
              >
                Xoa mem
              </button>
            </>
          ) : null}
        </div>
      </div>
    );
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
            {canReactivateArchived || canResumeSuspended ? (
              <button
                type="button"
                disabled={reactivateMutation.isPending || resumeMutation.isPending}
                onClick={() => setReactivateOpen(true)}
                className="flex items-center gap-2 rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
              >
                {reactivateMutation.isPending || resumeMutation.isPending ? "Dang kich hoat..." : "Kich hoat lai"}
              </button>
            ) : null}
          </div>
        </section>

        {guidanceMessage() ? (
          <section className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
            {guidanceMessage()}
          </section>
        ) : null}

        {/* Form */}
        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <form onSubmit={(event) => { event.preventDefault(); handleSave(); }} noValidate className="space-y-6">
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
                  disabled={saveMutation.isPending}
                  className="flex items-center gap-2 rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
                >
                  <Save className="h-4 w-4" />
                  {saveMutation.isPending ? "Dang luu..." : "Luu thay doi"}
                </button>
              </div>
            ) : (
              <div className="rounded-lg border border-dashed border-border bg-muted/50 px-4 py-3 text-sm text-muted-foreground">
                Listing hien chi cho phep chinh sua khi o trang thai DRAFT.
              </div>
            )}
          </form>
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold text-foreground">Extras</h2>
              <p className="text-sm text-muted-foreground">
                Quan ly cac tuy chon bo sung nhu GPS, ghe tre em hoac bao hiem.
              </p>
            </div>
          </div>

          <div className="mt-4 space-y-3">
            {extras.length > 0 ? extras.map(renderExtraRow) : (
              <div className="rounded-2xl border border-dashed border-border px-4 py-5 text-sm text-muted-foreground">
                Chua co extra nao duoc tao cho listing nay.
              </div>
            )}
          </div>

          {canEdit ? (
            <div className="mt-5 rounded-2xl border border-border/70 bg-background/70 p-4">
              <div className="flex items-center gap-2">
                <Plus className="h-4 w-4 text-primary" />
                <h3 className="text-sm font-semibold text-foreground">Them extra moi</h3>
              </div>
              <div className="mt-3 grid gap-3 md:grid-cols-[1.3fr_0.9fr_0.8fr_auto]">
                <input
                  value={extraDraft.name}
                  onChange={(event) => setExtraDraft((prev) => ({ ...prev, name: event.target.value }))}
                  placeholder="Vi du: GPS, ghe tre em"
                  className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
                />
                <select
                  value={extraDraft.pricingType}
                  onChange={(event) =>
                    setExtraDraft((prev) => ({
                      ...prev,
                      pricingType: event.target.value as "PER_DAY" | "PER_TRIP",
                    }))
                  }
                  className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
                >
                  <option value="PER_DAY">Theo ngay</option>
                  <option value="PER_TRIP">Theo chuyen</option>
                </select>
                <input
                  type="number"
                  min="0"
                  step="1000"
                  value={extraDraft.price}
                  onChange={(event) => setExtraDraft((prev) => ({ ...prev, price: event.target.value }))}
                  placeholder="Gia"
                  className="rounded-xl border border-border bg-background px-3 py-2 text-sm"
                />
                <button
                  type="button"
                  onClick={() => createExtraMutation.mutate()}
                  disabled={!extraDraft.name.trim() || !extraDraft.price || createExtraMutation.isPending}
                  className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:enabled:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {createExtraMutation.isPending ? "Dang them..." : "Them"}
                </button>
              </div>
            </div>
          ) : null}
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
        description={
          canResumeSuspended
            ? "Tin dang dang tam ngung se duoc dua lai trang thai hoat dong."
            : "Tin dang da luu kho se duoc dat lai ve trang thai nhap, cho phep ban chinh sua va gui duyet lai."
        }
        confirmLabel="Kich hoat"
        onClose={() => setReactivateOpen(false)}
        onConfirm={() => {
          if (canResumeSuspended) {
            resumeMutation.mutate();
            return;
          }
          reactivateMutation.mutate();
        }}
      />
    </WorkspaceSidebar>
  );
}
