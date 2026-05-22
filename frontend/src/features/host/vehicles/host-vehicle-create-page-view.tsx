"use client";

import Link from "next/link";
import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
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
  status: "ACTIVE",
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
      `Đã tạo xe (chế độ tĩnh): ${values.make} ${values.model} (${values.year}) trạng thái ${values.status}.`,
    );
    form.reset(INITIAL_FORM);
  }

  return (
    <AppShell activePath="/host/vehicles">
      <div className="space-y-6">
        <PageHeader
          title="Thêm xe"
          description="Tạo xe mới với kiểm tra local. Dữ liệu chỉ là tĩnh, chưa lưu lên server."
          actions={
            <Link
              href="/host/vehicles"
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Quay lại danh sách xe
            </Link>
          }
        />

        {successMessage ? (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            {successMessage}
          </section>
        ) : null}

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <form onSubmit={form.handleSubmit(handleSubmit)} noValidate className="space-y-4">
            <VehicleFormFields register={form.register} errors={form.formState.errors} />
            <div className="flex flex-wrap gap-2">
              <button
                type="submit"
                className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
              >
                Lưu xe
              </button>
              <button
                type="button"
                onClick={() => {
                  form.reset(INITIAL_FORM);
                  setSuccessMessage("");
                }}
                className="rounded-full border border-border bg-background px-5 py-2.5 text-sm font-semibold text-foreground hover:bg-accent"
              >
                Đặt lại
              </button>
            </div>
          </form>
        </section>
      </div>
    </AppShell>
  );
}
