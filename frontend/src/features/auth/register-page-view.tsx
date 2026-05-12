"use client";

import Link from "next/link";
import { useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { AuthCard } from "@/features/auth/auth-card";
import { AuthFormLayout } from "@/features/auth/auth-form-layout";
import type { AuthFormErrors, AuthFormState, AuthRoleOption } from "@/features/auth/types";
import { AUTH_DEMO_ERRORS, AUTH_ROLE_OPTIONS } from "@/mocks/auth";

function validateRegisterForm(form: AuthFormState): AuthFormErrors {
  const errors: AuthFormErrors = {};

  if (!form.fullName.trim()) {
    errors.fullName = "Full name is required.";
  }

  if (!form.email.trim()) {
    errors.email = "Email is required.";
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    errors.email = "Enter a valid email address.";
  }

  if (!form.password) {
    errors.password = "Password is required.";
  } else if (form.password.length < 8) {
    errors.password = "Password must be at least 8 characters.";
  }

  if (form.roles.length === 0) {
    errors.roles = "Choose at least one role.";
  }

  return errors;
}

export function RegisterPageView() {
  const [form, setForm] = useState<AuthFormState>({
    email: "",
    password: "",
    fullName: "",
    roles: ["CUSTOMER"],
  });
  const [errors, setErrors] = useState<AuthFormErrors>({});
  const [errorCode, setErrorCode] = useState<string>("");
  const [successMessage, setSuccessMessage] = useState<string>("");

  function updateField(field: Exclude<keyof AuthFormState, "roles">, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined }));
    setErrorCode("");
    setSuccessMessage("");
  }

  function toggleRole(role: AuthRoleOption) {
    setForm((prev) => {
      const hasRole = prev.roles.includes(role);
      const roles = hasRole ? prev.roles.filter((item) => item !== role) : [...prev.roles, role];
      return { ...prev, roles };
    });
    setErrors((prev) => ({ ...prev, roles: undefined }));
    setErrorCode("");
    setSuccessMessage("");
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateRegisterForm(form);
    setErrors(nextErrors);
    setErrorCode("");
    setSuccessMessage("");

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    if (form.email.toLowerCase() === AUTH_DEMO_ERRORS.duplicateEmail) {
      setErrorCode("USER_EMAIL_EXISTS");
      return;
    }

    setSuccessMessage(`Static registration success for ${form.roles.join(", ")} role(s).`);
  }

  return (
    <AppShell activePath="/register">
      <div className="py-6">
        <AuthCard
          title="Create RentFlow Account"
          description="Register as customer or host for the static Phase 5 experience."
        >
          <form onSubmit={handleSubmit} noValidate>
            <AuthFormLayout
              errorBanner={
                errorCode ? (
                  <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
                    {errorCode}: This email is already registered.
                  </div>
                ) : null
              }
              footer={
                <p>
                  Already have an account?{" "}
                  <Link href="/login" className="font-semibold text-primary hover:underline">
                    Login
                  </Link>
                </p>
              }
            >
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Full name</label>
                <input
                  type="text"
                  value={form.fullName}
                  onChange={(event) => updateField("fullName", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.fullName ? (
                  <p className="mt-1 text-xs text-red-700">{errors.fullName}</p>
                ) : null}
              </div>

              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Email</label>
                <input
                  type="email"
                  value={form.email}
                  onChange={(event) => updateField("email", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.email ? <p className="mt-1 text-xs text-red-700">{errors.email}</p> : null}
              </div>

              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Password</label>
                <input
                  type="password"
                  value={form.password}
                  onChange={(event) => updateField("password", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.password ? (
                  <p className="mt-1 text-xs text-red-700">{errors.password}</p>
                ) : null}
              </div>

              <div>
                <p className="mb-2 text-sm font-semibold text-foreground">Roles</p>
                <div className="flex flex-wrap gap-2">
                  {AUTH_ROLE_OPTIONS.map((roleOption) => {
                    const selected = form.roles.includes(roleOption.value);
                    return (
                      <button
                        key={roleOption.value}
                        type="button"
                        onClick={() => toggleRole(roleOption.value)}
                        className={[
                          "rounded-full border px-3 py-1.5 text-sm font-semibold transition-colors",
                          selected
                            ? "border-primary bg-primary text-primary-foreground"
                            : "border-border bg-background text-foreground hover:bg-accent",
                        ].join(" ")}
                      >
                        {roleOption.label}
                      </button>
                    );
                  })}
                </div>
                {errors.roles ? <p className="mt-1 text-xs text-red-700">{errors.roles}</p> : null}
              </div>

              <button
                type="submit"
                className="h-10 w-full rounded-full bg-primary text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
              >
                Create account
              </button>

              {successMessage ? (
                <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
                  {successMessage}
                </div>
              ) : null}
            </AuthFormLayout>
          </form>
        </AuthCard>
      </div>
    </AppShell>
  );
}
