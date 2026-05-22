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
    errors.fullName = "Full name is required.";
  }

  if (form.phone.trim() && !/^\+?[0-9\-\s]{7,20}$/.test(form.phone.trim())) {
    errors.phone = "Enter a valid phone number.";
  }

  if (form.dateOfBirth) {
    const dob = Date.parse(`${form.dateOfBirth}T00:00:00`);
    const now = Date.now();
    if (Number.isNaN(dob)) {
      errors.dateOfBirth = "Invalid date of birth.";
    } else if (dob > now) {
      errors.dateOfBirth = "Date of birth cannot be in the future.";
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
      setBanner("Profile saved.");
    },
    onError: () => {
      toast.error("Failed to save profile. Please try again.");
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
          <p className="text-sm text-muted-foreground">Loading profile...</p>
        </section>
      </AppShell>
    );
  }

  if (!profile) {
    return (
      <AppShell activePath="/me/profile">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">Unable to load profile.</p>
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
          title="Profile Settings"
          description="Edit personal fields. Email, roles, and verification remain read-only."
        />

        {banner ? (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            {banner}
          </section>
        ) : null}

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <h2 className="text-lg font-bold text-foreground">Account Snapshot</h2>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <div className="rounded-lg border border-border bg-background px-3 py-2">
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Email</p>
              <p className="mt-1 text-sm font-semibold text-foreground">{profile.email}</p>
            </div>
            <div className="rounded-lg border border-border bg-background px-3 py-2">
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Driver verification</p>
              <div className="mt-1">
                <StatusBadge status={profile.driverVerificationStatus} />
              </div>
            </div>
          </div>
          <div className="mt-3">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">Roles</p>
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
          <h2 className="text-lg font-bold text-foreground">Editable Fields</h2>
          <form onSubmit={handleSave} noValidate className="mt-4 space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Full name</label>
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
                <label className="mb-1 block text-sm font-semibold text-foreground">Phone</label>
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
                <label className="mb-1 block text-sm font-semibold text-foreground">Date of birth</label>
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
                <label className="mb-1 block text-sm font-semibold text-foreground">Address</label>
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
              {saving ? "Saving..." : "Save profile"}
            </button>
          </form>
        </section>
      </div>
    </AppShell>
  );
}
