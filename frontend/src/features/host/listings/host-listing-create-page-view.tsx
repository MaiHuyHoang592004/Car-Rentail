"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { StatusBadge } from "@/components/rentflow/status-badge";
import type { HostListingFormErrors, HostListingFormState } from "@/features/host/forms";
import { ListingFormFields } from "@/features/host/listings/listing-form-fields";
import { validateListingForm } from "@/features/host/listings/listing-form-utils";
import { createListing } from "@/features/host/listings/api";
import { getHostActiveVehicles } from "@/features/host/vehicles/api";
import type { HostListingViewModel } from "@/features/host/types";

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
  const queryClient = useQueryClient();

  const { data: vehicleOptions = [], isLoading: loadingVehicles } = useQuery({
    queryKey: ["host", "vehicles", "ACTIVE"],
    queryFn: async () => {
      const opts = await getHostActiveVehicles();
      return opts.map((opt) => ({
        id: opt.id,
        make: opt.label.split(" ")[0] ?? "",
        model: opt.label.replace(/^[^\s]+\s*/, "").replace(/\s*\(\d+\)$/, ""),
        year: Number(opt.label.match(/\((\d+)\)$/)?.[1] ?? 0),
        category: "",
        transmission: "AUTO" as const,
        fuelType: "",
        seats: 0,
        status: "ACTIVE" as const,
        city: "",
        plateNumber: "",
        vin: "",
      }));
    },
  });

  const { mutate: doCreate, isPending: creating } = useMutation<HostListingViewModel, Error, Parameters<typeof createListing>[0]>({
    mutationFn: createListing,
    onSuccess: (listing) => {
      queryClient.invalidateQueries({ queryKey: ["host", "listings"] });
      setCreatedListing(listing);
      setForm(INITIAL_FORM);
      toast.success("Listing created successfully.");
    },
    onError: () => {
      toast.error("Failed to create listing. Please try again.");
    },
  });

  const [form, setForm] = useState<HostListingFormState>(INITIAL_FORM);
  const [errors, setErrors] = useState<HostListingFormErrors>({});
  const [createdListing, setCreatedListing] = useState<HostListingViewModel | null>(null);

  function updateField<K extends keyof HostListingFormState>(field: K, value: HostListingFormState[K]) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined, form: undefined }));
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreatedListing(null);

    const nextErrors = validateListingForm(form);
    setErrors(nextErrors);

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    const selectedVehicle = vehicleOptions.find((v) => v.id === form.vehicleId);
    if (!selectedVehicle) {
      setErrors({ vehicleId: "Select an ACTIVE vehicle." });
      return;
    }

    doCreate({
      vehicleId: form.vehicleId,
      title: form.title.trim(),
      description: form.description.trim(),
      city: form.city.trim(),
      address: form.address.trim(),
      basePricePerDay: Number(form.basePricePerDay),
      dailyKmLimit: Number(form.dailyKmLimit),
      instantBook: form.instantBook,
      cancellationPolicy: form.cancellationPolicy,
    });
  }

  return (
    <AppShell activePath="/host/listings">
      <div className="space-y-6">
        <PageHeader
          title="Create Listing"
          description="Create a new listing draft for an ACTIVE vehicle."
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
            <p className="text-sm font-semibold">Listing created successfully.</p>
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
                disabled={creating}
                className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {creating ? "Creating..." : "Save as draft"}
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
