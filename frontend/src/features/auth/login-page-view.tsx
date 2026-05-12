"use client";

import Link from "next/link";
import { useState } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { AuthCard } from "@/features/auth/auth-card";
import { AuthFormLayout } from "@/features/auth/auth-form-layout";
import type { AuthFormErrors, AuthFormState, GuestIntentRedirect } from "@/features/auth/types";
import { AUTH_DEMO_ERRORS } from "@/mocks/auth";

function validateLoginForm(form: AuthFormState): AuthFormErrors {
  const errors: AuthFormErrors = {};

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

  return errors;
}

type LoginPageViewProps = {
  redirectIntent: GuestIntentRedirect;
};

export function LoginPageView({ redirectIntent }: LoginPageViewProps) {

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

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateLoginForm(form);
    setErrors(nextErrors);
    setErrorCode("");
    setSuccessMessage("");

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    if (form.email.toLowerCase() === AUTH_DEMO_ERRORS.invalidCredentialsEmail) {
      setErrorCode("AUTH_INVALID_CREDENTIALS");
      return;
    }

    setSuccessMessage(`Static login success. Redirect target: ${redirectIntent.nextPath}`);
  }

  return (
    <AppShell activePath="/login">
      <div className="py-6">
        <AuthCard
          title="Login to RentFlow"
          description="Sign in to continue booking, hosting, or admin operations."
        >
          <form onSubmit={handleSubmit} noValidate>
            <AuthFormLayout
              errorBanner={
                errorCode ? (
                  <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
                    {errorCode}: Invalid email or password.
                  </div>
                ) : null
              }
              footer={
                <p>
                  No account yet?{" "}
                  <Link href="/register" className="font-semibold text-primary hover:underline">
                    Create account
                  </Link>
                </p>
              }
            >
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

              <button
                type="submit"
                className="h-10 w-full rounded-full bg-primary text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
              >
                Login
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
