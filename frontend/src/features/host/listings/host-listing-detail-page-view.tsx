"use client";

import Link from "next/link";
import { useMemo, useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { StatusBadge } from "@/components/rentflow/status-badge";
import type { HostListingFormErrors, HostListingFormState } from "@/features/host/forms";
import { HostActionDialog } from "@/features/host/components/host-action-dialog";
import { ListingFormFields } from "@/features/host/listings/listing-form-fields";
import { buildListingFormFromViewModel, validateListingForm } from "@/features/host/listings/listing-form-utils";
import type { HostListingViewModel } from "@/features/host/types";
import {
  archiveListingTransition,
  getHostListingById,
  reactivateListingTransition,
  submitListingTransition,
} from "@/mocks/host-listings";
import { getHostActiveVehicles, getHostVehicleById } from "@/mocks/vehicles";

type HostListingDetailPageViewProps = {
  listingId: string;
};

export function HostListingDetailPageView({ listingId }: HostListingDetailPageViewProps) {
  const initialListing = getHostListingById(listingId);
  const [listing, setListing] = useState<HostListingViewModel | null>(initialListing);
  const [form, setForm] = useState<HostListingFormState | null>(
    initialListing ? buildListingFormFromViewModel(initialListing) : null,
  );
  const [errors, setErrors] = useState<HostListingFormErrors>({});
  const [banner, setBanner] = useState<string>("");
  const [archiveOpen, setArchiveOpen] = useState<boolean>(false);
  const [reactivateOpen, setReactivateOpen] = useState<boolean>(false);

  const vehicleOptions = useMemo(() => {
    if (!listing) {
      return [];
    }
    const activeVehicles = getHostActiveVehicles();
    const currentVehicleExists = activeVehicles.some((vehicle) => vehicle.id === listing.vehicleId);
    if (currentVehicleExists) {
      return activeVehicles;
    }
    const currentVehicle = getHostVehicleById(listing.vehicleId);
    if (!currentVehicle) {
      return activeVehicles;
    }
    return [currentVehicle, ...activeVehicles];
  }, [listing]);

  if (!listing || !form) {
    return (
      <AppShell activePath="/host/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-3xl font-bold text-foreground">Listing not found</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            This static mock does not include the requested listing id.
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
  const listingData = listing;

  const canEdit = listingData.status === "DRAFT";
  const canSubmit = listingData.status === "DRAFT";
  const canArchive = ["DRAFT", "PENDING_APPROVAL", "ACTIVE"].includes(listingData.status);
  const canReactivate = listingData.status === "SUSPENDED";

  function updateField<K extends keyof HostListingFormState>(
    field: K,
    value: HostListingFormState[K],
  ) {
    setForm((prev) => (prev ? { ...prev, [field]: value } : prev));
    setErrors((prev) => ({ ...prev, [field]: undefined, form: undefined }));
    setBanner("");
  }

  function handleSave(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canEdit || !form) {
      return;
    }

    const nextErrors = validateListingForm(form);
    setErrors(nextErrors);
    setBanner("");
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    const selectedVehicle = vehicleOptions.find((vehicle) => vehicle.id === form.vehicleId);
    if (!selectedVehicle) {
      setErrors({ vehicleId: "Selected vehicle is not available." });
      return;
    }

    setListing((prev) => {
      if (!prev) {
        return prev;
      }
      return {
        ...prev,
        vehicleId: selectedVehicle.id,
        vehicleLabel: `${selectedVehicle.make} ${selectedVehicle.model} (${selectedVehicle.year})`,
        title: form.title.trim(),
        description: form.description.trim(),
        city: form.city.trim(),
        address: form.address.trim(),
        basePricePerDay: Number(form.basePricePerDay),
        dailyKmLimit: Number(form.dailyKmLimit),
        instantBook: form.instantBook,
        cancellationPolicy: form.cancellationPolicy,
      };
    });
    setBanner("Listing fields updated in static UI.");
  }

  function handleSubmitForApproval() {
    const nextListing = submitListingTransition(listingData);
    if (!nextListing) {
      setBanner("Submit action is not available for current status.");
      return;
    }
    setListing(nextListing);
    setBanner("Listing transitioned from DRAFT to PENDING_APPROVAL.");
  }

  function handleArchive() {
    const nextListing = archiveListingTransition(listingData);
    if (!nextListing) {
      setBanner("Archive action is not available for current status.");
      setArchiveOpen(false);
      return;
    }
    setListing(nextListing);
    setArchiveOpen(false);
    setBanner("Listing archived in static UI.");
  }

  function handleReactivate() {
    const nextListing = reactivateListingTransition(listingData);
    if (!nextListing) {
      setBanner("Reactivate action is not available for current status.");
      setReactivateOpen(false);
      return;
    }
    setListing(nextListing);
    setReactivateOpen(false);
    setBanner("Listing reactivated to ACTIVE in static UI.");
  }

  return (
    <AppShell activePath="/host/listings">
      <div className="space-y-6">
        <PageHeader
          title={`Listing Detail: ${listingData.id}`}
          description="Static preview and lifecycle actions for host listing management."
          actions={
            <Link
              href={`/host/listings/${listingData.id}/availability`}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Availability
            </Link>
          }
        />

        {banner ? (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            {banner}
          </section>
        ) : null}

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <div>
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Current status</p>
              <div className="mt-1">
                <StatusBadge status={listing.status} />
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                disabled={!canSubmit}
                onClick={handleSubmitForApproval}
                className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
              >
                Submit
              </button>
              <button
                type="button"
                disabled={!canArchive}
                onClick={() => setArchiveOpen(true)}
                className="rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
              >
                Archive
              </button>
              <button
                type="button"
                disabled={!canReactivate}
                onClick={() => setReactivateOpen(true)}
                className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:bg-accent"
              >
                Reactivate
              </button>
            </div>
          </div>

          <form onSubmit={handleSave} noValidate className="space-y-4">
            <ListingFormFields
              form={form}
              errors={errors}
              onChange={updateField}
              vehicleOptions={vehicleOptions}
              disableVehicleSelect
              readOnly={!canEdit}
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
        description="This static action transitions listing status to ARCHIVED when allowed."
        confirmLabel="Confirm archive"
        tone="danger"
        onClose={() => setArchiveOpen(false)}
        onConfirm={handleArchive}
      />

      <HostActionDialog
        open={reactivateOpen}
        title="Reactivate Listing"
        description="This static action transitions listing status from SUSPENDED to ACTIVE."
        confirmLabel="Reactivate"
        onClose={() => setReactivateOpen(false)}
        onConfirm={handleReactivate}
      />
    </AppShell>
  );
}
