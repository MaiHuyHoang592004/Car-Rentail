"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { StatusBadge } from "@/components/rentflow/status-badge";
import { vehicleFormSchema, type VehicleFormState } from "@/features/host/forms";
import { HostActionDialog } from "@/features/host/components/host-action-dialog";
import { VehicleFormFields } from "@/features/host/vehicles/vehicle-form-fields";
import { buildVehicleFormFromViewModel } from "@/features/host/vehicles/vehicle-form-utils";
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

  const form = useForm<VehicleFormState>({
    resolver: zodResolver(vehicleFormSchema),
  });
  const [archiveOpen, setArchiveOpen] = useState<boolean>(false);
  const [banner, setBanner] = useState<string>("");

  useEffect(() => {
    if (vehicle) {
      form.reset(buildVehicleFormFromViewModel(vehicle));
    }
  }, [form, vehicle]);

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

  if (!vehicle) {
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

  function handleSave(values: VehicleFormState) {
    setBanner("");
    saveVehicle({
      category: values.category.trim(),
      make: values.make.trim(),
      model: values.model.trim(),
      year: Number(values.year),
      transmission: values.transmission,
      fuelType: values.fuelType.trim(),
      seats: Number(values.seats),
      status: values.status,
      city: values.city.trim(),
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

          <form onSubmit={form.handleSubmit(handleSave)} noValidate className="space-y-4">
            <VehicleFormFields register={form.register} errors={form.formState.errors} />

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
