"use client";

import Link from "next/link";
import { useMemo, useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { StatusBadge } from "@/components/rentflow/status-badge";
import type { HostListingFormErrors, HostListingFormState } from "@/features/host/forms";
import { ListingFormFields } from "@/features/host/listings/listing-form-fields";
import { validateListingForm } from "@/features/host/listings/listing-form-utils";
import type { HostListingViewModel } from "@/features/host/types";
import { getHostActiveVehicles } from "@/mocks/vehicles";

const INITIAL_FORM: HostListingFormState = {
  vehicleId: "",
  title: "",
  description: "",
  city: "",
  address: "",
  basePricePerDay: "",
  dailyKmLimit: "",
  instantBook: false,
  cancellationPolicy: "FLEXIBLE",
};

export function HostListingCreatePageView() {
  const vehicleOptions = useMemo(() => getHostActiveVehicles(), []);
  const [form, setForm] = useState<HostListingFormState>(INITIAL_FORM);
  const [errors, setErrors] = useState<HostListingFormErrors>({});
  const [createdListing, setCreatedListing] = useState<HostListingViewModel | null>(null);

  function updateField<K extends keyof HostListingFormState>(field: K, value: HostListingFormState[K]) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined, form: undefined }));
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateListingForm(form);
    setErrors(nextErrors);
    setCreatedListing(null);

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    const selectedVehicle = vehicleOptions.find((vehicle) => vehicle.id === form.vehicleId);
    if (!selectedVehicle) {
      setErrors({ vehicleId: "Select an ACTIVE vehicle." });
      return;
    }

    const previewId = `hst-lst-new-${Date.now()}`;
    setCreatedListing({
      id: previewId,
      vehicleId: selectedVehicle.id,
      vehicleLabel: `${selectedVehicle.make} ${selectedVehicle.model} (${selectedVehicle.year})`,
      title: form.title.trim(),
      description: form.description.trim(),
      city: form.city.trim(),
      address: form.address.trim(),
      basePricePerDay: Number(form.basePricePerDay),
      currency: "VND",
      dailyKmLimit: Number(form.dailyKmLimit),
      instantBook: form.instantBook,
      cancellationPolicy: form.cancellationPolicy,
      status: "DRAFT",
    });
    setForm(INITIAL_FORM);
  }

  return (
    <AppShell activePath="/host/listings">
      <div className="space-y-6">
        <PageHeader
          title="Create Listing"
          description="Create a static listing draft using only ACTIVE host vehicles."
          actions={
            <Link
              href="/host/listings"
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Back to listings
            </Link>
          }
        />

        {createdListing ? (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-emerald-900">
            <p className="text-sm font-semibold">Static listing created in DRAFT state.</p>
            <div className="mt-2 flex items-center gap-2 text-sm">
              <span>{createdListing.title}</span>
              <StatusBadge status={createdListing.status} />
            </div>
          </section>
        ) : null}

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <ListingFormFields
              form={form}
              errors={errors}
              onChange={updateField}
              vehicleOptions={vehicleOptions}
            />
            <div className="flex flex-wrap gap-2">
              <button
                type="submit"
                className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
              >
                Save as draft
              </button>
              <button
                type="button"
                onClick={() => {
                  setForm(INITIAL_FORM);
                  setErrors({});
                  setCreatedListing(null);
                }}
                className="rounded-full border border-border bg-background px-5 py-2.5 text-sm font-semibold text-foreground hover:bg-accent"
              >
                Reset
              </button>
            </div>
          </form>
        </section>
      </div>
    </AppShell>
  );
}
