"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { StatusBadge } from "@/components/rentflow/status-badge";
import type { ProfileFormErrors, ProfileFormState } from "@/features/profile/types";
import { getProfile, updateProfile } from "@/features/profile/api";

function validateProfileForm(form: ProfileFormState): ProfileFormErrors {
  const errors: ProfileFormErrors = {};

  if (!form.fullName.trim()) {
    errors.fullName = "Họ và tên là bắt buộc.";
  }

  if (form.phone.trim() && !/^\+?[0-9\-\s]{7,20}$/.test(form.phone.trim())) {
    errors.phone = "Vui lòng nhập số điện thoại hợp lệ.";
  }

  if (form.dateOfBirth) {
    const dob = Date.parse(`${form.dateOfBirth}T00:00:00`);
    const now = Date.now();
    if (Number.isNaN(dob)) {
      errors.dateOfBirth = "Ngày sinh không hợp lệ.";
    } else if (dob > now) {
      errors.dateOfBirth = "Ngày sinh không được nằm trong tương lai.";
    }
  }

  return errors;
}

export function ProfilePageView() {
  const queryClient = useQueryClient();

  const { data: profile, isLoading } = useQuery({
    queryKey: ["profile"],
    queryFn: getProfile,
  });

  const { mutate: doUpdate, isPending: saving } = useMutation({
    mutationFn: updateProfile,
    onSuccess: (updated) => {
      queryClient.setQueryData(["profile"], updated);
      setBanner("Đã lưu hồ sơ.");
    },
    onError: () => {
      toast.error("Không thể lưu hồ sơ. Vui lòng thử lại.");
    },
  });

  const [form, setForm] = useState<ProfileFormState>({
    fullName: "",
    phone: "",
    dateOfBirth: "",
    addressLine: "",
  });
  const [errors, setErrors] = useState<ProfileFormErrors>({});
  const [banner, setBanner] = useState<string>("");

  if (isLoading) {
    return (
      <AppShell activePath="/me/profile">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">Đang tải hồ sơ...</p>
        </section>
      </AppShell>
    );
  }

  if (!profile) {
    return (
      <AppShell activePath="/me/profile">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">Không tải được hồ sơ.</p>
        </section>
      </AppShell>
    );
  }

  if (form.fullName === "") {
    setForm({
      fullName: profile.fullName,
      phone: profile.phone,
      dateOfBirth: profile.dateOfBirth,
      addressLine: profile.addressLine,
    });
  }

  function updateField(field: keyof ProfileFormState, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined, form: undefined }));
    setBanner("");
  }

  function handleSave(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateProfileForm(form);
    setErrors(nextErrors);
    setBanner("");

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    doUpdate({
      fullName: form.fullName.trim(),
      phone: form.phone.trim(),
      dateOfBirth: form.dateOfBirth || null,
      addressLine: form.addressLine.trim(),
    });
  }

  return (
    <AppShell activePath="/me/profile">
      <div className="space-y-6">
        <PageHeader
          title="Hồ sơ cá nhân"
          description="Cập nhật thông tin cá nhân. Email, vai trò và xác minh chỉ hiển thị, không sửa được."
        />

        {banner ? (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            {banner}
          </section>
        ) : null}

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <h2 className="text-lg font-bold text-foreground">Tổng quan tài khoản</h2>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <div className="rounded-lg border border-border bg-background px-3 py-2">
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Email</p>
              <p className="mt-1 text-sm font-semibold text-foreground">{profile.email}</p>
            </div>
            <div className="rounded-lg border border-border bg-background px-3 py-2">
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Xác minh GPLX</p>
              <div className="mt-1">
                <StatusBadge status={profile.driverVerificationStatus} />
              </div>
            </div>
          </div>
          <div className="mt-3">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">Vai trò</p>
            <div className="mt-1 flex flex-wrap gap-2">
              {profile.roles.map((role) => (
                <span
                  key={role}
                  className="rounded-full border border-border bg-background px-2.5 py-1 text-xs font-semibold text-foreground"
                >
                  {role}
                </span>
              ))}
            </div>
          </div>
        </section>

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <h2 className="text-lg font-bold text-foreground">Thông tin có thể chỉnh sửa</h2>
          <form onSubmit={handleSave} noValidate className="mt-4 space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Họ và tên</label>
                <input
                  type="text"
                  value={form.fullName}
                  onChange={(event) => updateField("fullName", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.fullName ? (
                  <p className="mt-1 text-xs text-rose-700">{errors.fullName}</p>
                ) : null}
              </div>

              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Số điện thoại</label>
                <input
                  type="tel"
                  value={form.phone}
                  onChange={(event) => updateField("phone", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.phone ? <p className="mt-1 text-xs text-rose-700">{errors.phone}</p> : null}
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Ngày sinh</label>
                <input
                  type="date"
                  value={form.dateOfBirth}
                  onChange={(event) => updateField("dateOfBirth", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.dateOfBirth ? (
                  <p className="mt-1 text-xs text-rose-700">{errors.dateOfBirth}</p>
                ) : null}
              </div>

              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Địa chỉ</label>
                <input
                  type="text"
                  value={form.addressLine}
                  onChange={(event) => updateField("addressLine", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={saving}
              className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {saving ? "Đang lưu..." : "Lưu hồ sơ"}
            </button>
          </form>
        </section>
      </div>
    </AppShell>
  );
}
