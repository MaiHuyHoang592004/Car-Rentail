"use client";

import Link from "next/link";
import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { ArrowLeft, Plus } from "lucide-react";

import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { vehicleFormSchema, type VehicleFormState } from "@/features/host/forms";
import { VehicleFormFields } from "@/features/host/vehicles/vehicle-form-fields";

const INITIAL_FORM: VehicleFormState = {
  category: "",
  make: "",
  model: "",
  year: "",
  transmission: "AUTO",
  fuelType: "GASOLINE",
  seats: "",
  status: "DRAFT",
  city: "",
  plateNumber: "",
  vin: "",
};

export function HostVehicleCreatePageView() {
  const form = useForm<VehicleFormState>({
    resolver: zodResolver(vehicleFormSchema),
    defaultValues: INITIAL_FORM,
  });
  const [successMessage, setSuccessMessage] = useState<string>("");

  function handleSubmit(values: VehicleFormState) {
    setSuccessMessage("");
    setSuccessMessage(
      `Da tao xe (che do tinh): ${values.make} ${values.model} (${values.year}) trang thai ${values.status}.`,
    );
    form.reset(INITIAL_FORM);
  }

  return (
    <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/vehicles">
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-foreground">Them xe</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Tao xe moi trong he thong. Du lieu chi luu o local.
            </p>
          </div>
          <Link
            href="/host/vehicles"
            className="flex items-center gap-1.5 rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
          >
            <ArrowLeft className="h-4 w-4" />
            Quay lai
          </Link>
        </div>

        {successMessage ? (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            {successMessage}
          </section>
        ) : null}

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <form onSubmit={form.handleSubmit(handleSubmit)} noValidate className="space-y-6">
            <VehicleFormFields
              register={form.register}
              errors={form.formState.errors}
              hideStatus={true}
            />

            <div className="flex flex-wrap gap-2">
              <button
                type="submit"
                className="flex items-center gap-2 rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
              >
                <Plus className="h-4 w-4" />
                Luu xe
              </button>
              <button
                type="button"
                onClick={() => {
                  form.reset(INITIAL_FORM);
                  setSuccessMessage("");
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