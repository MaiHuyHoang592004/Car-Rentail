"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { StatusBadge } from "@/components/rentflow/status-badge";
import type { VehicleFormErrors, VehicleFormState } from "@/features/host/forms";
import { HostActionDialog } from "@/features/host/components/host-action-dialog";
import { VehicleFormFields } from "@/features/host/vehicles/vehicle-form-fields";
import { buildVehicleFormFromViewModel, validateVehicleForm } from "@/features/host/vehicles/vehicle-form-utils";
import { archiveHostVehicle, getHostVehicleById, updateHostVehicle } from "@/features/host/vehicles/api";

type HostVehicleDetailPageViewProps = {
  vehicleId: string;
};

export function HostVehicleDetailPageView({ vehicleId }: HostVehicleDetailPageViewProps) {
  const queryClient = useQueryClient();

  const { data: vehicle, isLoading: loadingVehicle } = useQuery({
    queryKey: ["host", "vehicles", vehicleId],
    queryFn: () => getHostVehicleById(vehicleId),
    enabled: true,
  });

  const [form, setForm] = useState<VehicleFormState | null>(
    vehicle ? buildVehicleFormFromViewModel(vehicle) : null,
  );
  const [errors, setErrors] = useState<VehicleFormErrors>({});
  const [archiveOpen, setArchiveOpen] = useState<boolean>(false);
  const [banner, setBanner] = useState<string>("");

  const { mutate: saveVehicle, isPending: saving } = useMutation({
    mutationFn: (body: Parameters<typeof updateHostVehicle>[1]) =>
      updateHostVehicle(vehicleId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["host", "vehicles", vehicleId] });
      setBanner("Vehicle details saved.");
    },
    onError: () => {
      toast.error("Failed to save vehicle. Please try again.");
    },
  });

  const { mutate: doArchive, isPending: archiving } = useMutation({
    mutationFn: () => archiveHostVehicle(vehicleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["host", "vehicles", vehicleId] });
      queryClient.invalidateQueries({ queryKey: ["host", "vehicles"] });
      setBanner("Vehicle archived.");
      setArchiveOpen(false);
    },
    onError: () => {
      toast.error("Failed to archive vehicle. Please try again.");
    },
  });

  if (loadingVehicle) {
    return (
      <AppShell activePath="/host/vehicles">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">Loading vehicle...</p>
        </section>
      </AppShell>
    );
  }

  if (!vehicle || !form) {
    return (
      <AppShell activePath="/host/vehicles">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-3xl font-bold text-foreground">Vehicle not found</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            This vehicle does not exist or you do not have access.
          </p>
          <Link
            href="/host/vehicles"
            className="mt-4 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Back to vehicles
          </Link>
        </section>
      </AppShell>
    );
  }

  function updateField<K extends keyof VehicleFormState>(field: K, value: VehicleFormState[K]) {
    setForm((prev) => (prev ? { ...prev, [field]: value } : prev));
    setErrors((prev) => ({ ...prev, [field]: undefined, form: undefined }));
    setBanner("");
  }

  function handleSave(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const currentForm = form;
    if (!currentForm) {
      return;
    }
    const nextErrors = validateVehicleForm(currentForm);
    setErrors(nextErrors);
    setBanner("");

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    saveVehicle({
      category: currentForm.category.trim(),
      make: currentForm.make.trim(),
      model: currentForm.model.trim(),
      year: Number(currentForm.year),
      transmission: currentForm.transmission,
      fuelType: currentForm.fuelType.trim(),
      seats: Number(currentForm.seats),
      status: currentForm.status,
      city: currentForm.city.trim(),
    });
  }

  function handleArchive() {
    doArchive();
  }

  const canArchive = vehicle.status !== "ARCHIVED";

  return (
    <AppShell activePath="/host/vehicles">
      <div className="space-y-6">
        <PageHeader
          title={`Vehicle Detail: ${vehicle.id}`}
          description="Update vehicle details or archive this vehicle."
          actions={
            <Link
              href="/host/vehicles"
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Back to vehicles
            </Link>
          }
        />

        {banner ? (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            {banner}
          </section>
        ) : null}

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <div className="mb-4 flex items-center justify-between">
            <p className="text-sm text-muted-foreground">
              {vehicle.make} {vehicle.model} ({vehicle.year})
            </p>
            <StatusBadge status={vehicle.status} />
          </div>

          <form onSubmit={handleSave} noValidate className="space-y-4">
            <VehicleFormFields form={form} errors={errors} onChange={updateField} />

            <div className="flex flex-wrap gap-2">
              <button
                type="submit"
                disabled={saving}
                className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {saving ? "Saving..." : "Save changes"}
              </button>
              <button
                type="button"
                onClick={() => setArchiveOpen(true)}
                disabled={!canArchive || archiving}
                className="rounded-full bg-rose-600 px-5 py-2.5 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
              >
                {archiving ? "Archiving..." : "Archive vehicle"}
              </button>
            </div>
          </form>
        </section>
      </div>

      <HostActionDialog
        open={archiveOpen}
        title="Archive Vehicle"
        description="This vehicle and its listings will be archived. Existing bookings will not be affected."
        confirmLabel="Confirm archive"
        tone="danger"
        onClose={() => setArchiveOpen(false)}
        onConfirm={handleArchive}
      />
    </AppShell>
  );
}
