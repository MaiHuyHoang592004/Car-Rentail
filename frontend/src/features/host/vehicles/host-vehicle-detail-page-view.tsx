"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { ArrowLeft, Save, Trash2 } from "lucide-react";

import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { StatusBadge } from "@/components/rentflow/status-badge";
import { vehicleFormSchema, type VehicleFormState } from "@/features/host/forms";
import { HostActionDialog } from "@/features/host/components/host-action-dialog";
import { VehicleFormFields } from "@/features/host/vehicles/vehicle-form-fields";
import { buildVehicleFormFromViewModel } from "@/features/host/vehicles/vehicle-form-utils";
import {
  archiveHostVehicle,
  getHostVehicleArchivePreview,
  getHostVehicleById,
  updateHostVehicle,
} from "@/features/host/vehicles/api";
import { getVehicleStatusLabel } from "@/lib/display-labels";

type HostVehicleDetailPageViewProps = {
  vehicleId: string;
};

export function HostVehicleDetailPageView({ vehicleId }: HostVehicleDetailPageViewProps) {
  const queryClient = useQueryClient();
  const [archiveOpen, setArchiveOpen] = useState<boolean>(false);
  const [banner, setBanner] = useState<string>("");

  const { data: vehicle, isLoading: loadingVehicle } = useQuery({
    queryKey: ["host", "vehicles", vehicleId],
    queryFn: () => getHostVehicleById(vehicleId),
    enabled: true,
  });

  const { data: archivePreview } = useQuery({
    queryKey: ["host", "vehicles", vehicleId, "archive-preview"],
    queryFn: () => getHostVehicleArchivePreview(vehicleId),
    enabled: !!vehicle && archiveOpen,
  });

  const form = useForm<VehicleFormState>({
    resolver: zodResolver(vehicleFormSchema),
  });

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
      setBanner("Da luu thong tin xe thanh cong.");
    },
    onError: () => {
      toast.error("Loi khi luu xe. Vui long thu lai.");
    },
  });

  const { mutate: doArchive, isPending: archiving } = useMutation({
    mutationFn: () => archiveHostVehicle(vehicleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["host", "vehicles", vehicleId] });
      queryClient.invalidateQueries({ queryKey: ["host", "vehicles"] });
      setBanner("Da luu kho xe thanh cong.");
      setArchiveOpen(false);
    },
    onError: () => {
      toast.error("Loi khi luu kho xe. Vui long thu lai.");
    },
  });

  if (loadingVehicle) {
    return (
      <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/vehicles">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">Dang tai thong tin xe...</p>
        </section>
      </WorkspaceSidebar>
    );
  }

  if (!vehicle) {
    return (
      <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/vehicles">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-2xl font-bold text-foreground">Khong tim thay xe</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Xe nay khong ton tai hoac ban khong co quyen truy cap.
          </p>
          <Link
            href="/host/vehicles"
            className="mt-4 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Quay lai danh sach xe
          </Link>
        </section>
      </WorkspaceSidebar>
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
    if (archivePreview && !archivePreview.archiveAllowed) {
      toast.error(archivePreview.blockingReason ?? "Xe nay chua the luu kho.");
      return;
    }
    doArchive();
  }

  const canArchive = vehicle.status !== "ARCHIVED";

  return (
    <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/vehicles">
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link
              href="/host/vehicles"
              className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <ArrowLeft className="h-4 w-4" />
              Quay lai
            </Link>
            <span className="text-muted-foreground">|</span>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold text-foreground">
                {vehicle.make} {vehicle.model}
              </h1>
              <StatusBadge
                status={vehicle.status}
                label={getVehicleStatusLabel(vehicle.status)}
              />
            </div>
          </div>
        </div>

        {banner ? (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            {banner}
          </section>
        ) : null}

        {/* Form */}
        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <div className="mb-4 rounded-lg border border-border bg-background p-3">
            <p className="text-xs text-muted-foreground">
              Bien so: <strong className="text-foreground">{vehicle.plateNumber}</strong>
              {vehicle.vin ? (
                <span className="ml-3">VIN: <strong className="text-foreground">{vehicle.vin}</strong></span>
              ) : null}
            </p>
          </div>

          <form onSubmit={form.handleSubmit(handleSave)} noValidate className="space-y-6">
            <VehicleFormFields
              register={form.register}
              setValue={form.setValue}
              watch={form.watch}
              errors={form.formState.errors}
              hideStatus={false}
            />

            <div className="flex flex-wrap gap-2">
              <button
                type="submit"
                disabled={saving}
                className="flex items-center gap-2 rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Save className="h-4 w-4" />
                {saving ? "Dang luu..." : "Luu thay doi"}
              </button>
              <button
                type="button"
                onClick={() => setArchiveOpen(true)}
                disabled={!canArchive || archiving}
                className="flex items-center gap-2 rounded-full bg-rose-600 px-5 py-2.5 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
              >
                <Trash2 className="h-4 w-4" />
                {archiving ? "Dang luu kho..." : "Luu kho xe"}
              </button>
            </div>
          </form>
        </section>
      </div>

      <HostActionDialog
        open={archiveOpen}
        title="Luu kho xe"
        description={
          archivePreview
            ? [
                archivePreview.blockingReason ?? "Xe nay va cac tin dang lien quan se duoc luu kho.",
                archivePreview.affectedListings.length > 0
                  ? `Tin bi anh huong: ${archivePreview.affectedListings.map((item) => item.title).join(", ")}`
                  : "Khong co listing nao dang lien ket.",
              ].join(" ")
            : "Xe nay va cac tin dang lien quan se duoc luu kho. Cac don dat xe hien tai khong bi anh huong."
        }
        confirmLabel="Xac nhan luu kho"
        tone="danger"
        onClose={() => setArchiveOpen(false)}
        onConfirm={handleArchive}
        hideReason
      />
    </WorkspaceSidebar>
  );
}
