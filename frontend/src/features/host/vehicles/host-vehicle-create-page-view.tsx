"use client";

import Link from "next/link";
import { useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import type { VehicleFormErrors, VehicleFormState } from "@/features/host/forms";
import { VehicleFormFields } from "@/features/host/vehicles/vehicle-form-fields";
import { validateVehicleForm } from "@/features/host/vehicles/vehicle-form-utils";

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
  const [form, setForm] = useState<VehicleFormState>(INITIAL_FORM);
  const [errors, setErrors] = useState<VehicleFormErrors>({});
  const [successMessage, setSuccessMessage] = useState<string>("");

  function updateField<K extends keyof VehicleFormState>(field: K, value: VehicleFormState[K]) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined, form: undefined }));
    setSuccessMessage("");
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateVehicleForm(form);
    setErrors(nextErrors);
    setSuccessMessage("");

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setSuccessMessage(
      `Đã tạo xe (chế độ tĩnh): ${form.make} ${form.model} (${form.year}) trạng thái ${form.status}.`,
    );
    setForm(INITIAL_FORM);
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
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <VehicleFormFields form={form} errors={errors} onChange={updateField} />
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
                  setForm(INITIAL_FORM);
                  setErrors({});
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
