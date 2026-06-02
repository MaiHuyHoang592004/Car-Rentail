"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { ArrowLeft, Plus } from "lucide-react";

import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { StatusBadge } from "@/components/rentflow/status-badge";
import { listingFormSchema, type HostListingFormState } from "@/features/host/forms";
import { ListingFormFields } from "@/features/host/listings/listing-form-fields";
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

  const { data: vehicleOptions = [] } = useQuery({
    queryKey: ["host", "vehicles", "ACTIVE"],
    queryFn: async () => {
      const opts = await getHostActiveVehicles();
      return opts;
    },
  });

  const { mutate: doCreate, isPending: creating } = useMutation<HostListingViewModel, Error, Parameters<typeof createListing>[0]>({
    mutationFn: createListing,
    onSuccess: (listing) => {
      queryClient.invalidateQueries({ queryKey: ["host", "listings"] });
      setCreatedListing(listing);
      form.reset(INITIAL_FORM);
      toast.success("Tao tin dang thanh cong.");
    },
    onError: () => {
      toast.error("Loi khi tao tin dang. Vui long thu lai.");
    },
  });

  const form = useForm<HostListingFormState>({
    resolver: zodResolver(listingFormSchema),
    defaultValues: INITIAL_FORM,
  });
  const [createdListing, setCreatedListing] = useState<HostListingViewModel | null>(null);

  function handleSubmit(values: HostListingFormState) {
    setCreatedListing(null);
    const selectedVehicle = vehicleOptions.find((v) => v.id === values.vehicleId);
    if (!selectedVehicle) {
      form.setError("vehicleId", { message: "Vui long chon xe hoat dong." });
      return;
    }

    doCreate({
      vehicleId: values.vehicleId,
      title: values.title.trim(),
      description: values.description.trim(),
      city: values.city.trim(),
      address: values.address.trim(),
      basePricePerDay: Number(values.basePricePerDay),
      dailyKmLimit: Number(values.dailyKmLimit),
      instantBook: values.instantBook,
      cancellationPolicy: values.cancellationPolicy,
    });
  }

  return (
    <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/listings">
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-foreground">Tao tin dang</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Tao tin dang moi cho xe hoat dong. Tin se duoc luu o trang thai nhap.
            </p>
          </div>
          <Link
            href="/host/listings"
            className="flex items-center gap-1.5 rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
          >
            <ArrowLeft className="h-4 w-4" />
            Quay lai
          </Link>
        </div>

        {createdListing ? (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
            <p className="text-sm font-semibold text-emerald-900">Tao tin dang thanh cong.</p>
            <div className="mt-2 flex items-center gap-2 text-sm">
              <span className="text-emerald-900">{createdListing.title}</span>
              <StatusBadge status={createdListing.status} label="Nhap (Draft)" />
            </div>
            <Link
              href={`/host/listings/${createdListing.id}`}
              className="mt-2 inline-flex items-center gap-1 text-xs font-semibold text-emerald-700 underline hover:text-emerald-900"
            >
              Gui duyet ngay
            </Link>
          </section>
        ) : null}

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <form onSubmit={form.handleSubmit(handleSubmit)} noValidate className="space-y-6">
            <ListingFormFields
              register={form.register}
              errors={form.formState.errors}
              setValue={form.setValue}
              watch={form.watch}
              vehicleOptions={vehicleOptions}
            />
            <div className="flex flex-wrap gap-2">
              <button
                type="submit"
                disabled={creating}
                className="flex items-center gap-2 rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Plus className="h-4 w-4" />
                {creating ? "Dang tao..." : "Luu nhap"}
              </button>
              <button
                type="button"
                onClick={() => {
                  form.reset(INITIAL_FORM);
                  setCreatedListing(null);
                }}
                className="rounded-full border border-border bg-background px-5 py-2.5 text-sm font-semibold text-foreground hover:bg-accent"
              >
                Dat lai
              </button>
            </div>
          </form>
        </section>
      </div>
    </WorkspaceSidebar>
  );
}
