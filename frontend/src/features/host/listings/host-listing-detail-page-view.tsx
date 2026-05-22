"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
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
      setBanner({ type: "success", message: "Listing submitted for approval." });
    },
    onError: (err: ListingTransitionError) => {
      setBanner({ type: "error", message: err.message || "Failed to submit listing." });
    },
  });

  const archiveMutation = useMutation({
    mutationFn: () => archiveListingSafe(listingId),
    onSuccess: (updated) => {
      queryClient.setQueryData(["host", "listings", listingId], updated);
      setBanner({ type: "success", message: "Listing archived." });
      setArchiveOpen(false);
    },
    onError: (err: ListingTransitionError) => {
      setBanner({ type: "error", message: err.message || "Failed to archive listing." });
      setArchiveOpen(false);
    },
  });

  const reactivateMutation = useMutation({
    mutationFn: () => reactivateListingSafe(listingId),
    onSuccess: (updated) => {
      queryClient.setQueryData(["host", "listings", listingId], updated);
      setBanner({ type: "success", message: "Listing reactivated." });
      setReactivateOpen(false);
    },
    onError: (err: ListingTransitionError) => {
      setBanner({ type: "error", message: err.message || "Failed to reactivate listing." });
      setReactivateOpen(false);
    },
  });

  if (isLoading) {
    return (
      <AppShell activePath="/host/listings">
        <div className="flex items-center justify-center p-20">
          <p className="text-sm text-muted-foreground">Loading listing...</p>
        </div>
      </AppShell>
    );
  }

  if (isError || !listing) {
    return (
      <AppShell activePath="/host/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-3xl font-bold text-foreground">Listing not found</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            The listing does not exist or you do not have permission to view it.
          </p>
          <Link
            href="/host/listings"
            className="mt-4 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Back to listings
          </Link>
        </section>
      </AppShell>
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
    setBanner(null);
    setBanner({ type: "success", message: "Save is read-only in this implementation. Use the API to update listings." });
  }

  function handleSubmitForApproval() {
    submitMutation.mutate();
  }

  function handleArchive() {
    archiveMutation.mutate();
  }

  function handleReactivate() {
    reactivateMutation.mutate();
  }

  return (
    <AppShell activePath="/host/listings">
      <div className="space-y-6">
        <PageHeader
          title={`Listing Detail: ${listingId}`}
          description="Manage listing information and lifecycle actions."
          actions={
            <Link
              href={`/host/listings/${listingId}/availability`}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Availability
            </Link>
          }
        />

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

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <div>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Current status</p>
              <div className="mt-1">
                <StatusBadge status={currentListing.status} />
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                disabled={!canSubmit || submitMutation.isPending}
                onClick={handleSubmitForApproval}
                className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
              >
                {submitMutation.isPending ? "Submitting..." : "Submit"}
              </button>
              <button
                type="button"
                disabled={!canArchive || archiveMutation.isPending}
                onClick={() => setArchiveOpen(true)}
                className="rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
              >
                {archiveMutation.isPending ? "Archiving..." : "Archive"}
              </button>
              <button
                type="button"
                disabled={!canReactivate || reactivateMutation.isPending}
                onClick={() => setReactivateOpen(true)}
                className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:bg-accent"
              >
                {reactivateMutation.isPending ? "Reactivating..." : "Reactivate"}
              </button>
            </div>
          </div>

          <form onSubmit={form.handleSubmit(handleSave)} noValidate className="space-y-4">
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
            <div className="flex flex-wrap gap-2">
              <button
                type="submit"
                disabled={!canEdit}
                className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
              >
                Save changes
              </button>
              <Link
                href="/host/listings"
                className="rounded-full border border-border bg-background px-5 py-2.5 text-sm font-semibold text-foreground hover:bg-accent"
              >
                Back to listings
              </Link>
            </div>
          </form>
        </section>
      </div>

      <HostActionDialog
        open={archiveOpen}
        title="Archive Listing"
        description="This listing will be moved to archived status. It will no longer be visible to renters."
        confirmLabel="Confirm archive"
        tone="danger"
        onClose={() => setArchiveOpen(false)}
        onConfirm={handleArchive}
      />

      <HostActionDialog
        open={reactivateOpen}
        title="Reactivate Listing"
        description="This archived listing will be reset to DRAFT status, allowing you to edit and resubmit."
        confirmLabel="Reactivate"
        onClose={() => setReactivateOpen(false)}
        onConfirm={handleReactivate}
      />
    </AppShell>
  );
}
