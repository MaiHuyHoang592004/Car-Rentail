"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { AppShell } from "@/components/rentflow/app-shell";
import { ErrorBanner } from "@/components/rentflow/error-banner";
import { AuthCard } from "@/features/auth/auth-card";
import { AuthFormLayout } from "@/features/auth/auth-form-layout";
import { useAuth } from "@/features/auth/auth-context";
import { ApiError } from "@/lib/api-error";

const registerSchema = z.object({
  fullName: z.string().trim().min(1, "Vui lòng nhập họ tên."),
  email: z.string().min(1, "Vui lòng nhập email.").email("Email không hợp lệ."),
  password: z
    .string()
    .min(8, "Mật khẩu cần ít nhất 8 ký tự."),
  roles: z
    .array(z.enum(["CUSTOMER", "HOST"]))
    .min(1, "Chọn ít nhất một vai trò."),
});

type RegisterForm = z.infer<typeof registerSchema>;

const ROLE_OPTIONS: { value: "CUSTOMER" | "HOST"; label: string; hint?: string }[] = [
  { value: "CUSTOMER", label: "Khách thuê" },
  {
    value: "HOST",
    label: "Chủ xe (Host)",
    hint: "Tài khoản Host cần xác minh giấy phép kinh doanh trước khi đăng tin.",
  },
];

export function RegisterPageView() {
  const router = useRouter();
  const { register: registerUser } = useAuth();
  const [submitError, setSubmitError] = useState<ApiError | null>(null);

  const form = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      fullName: "",
      email: "",
      password: "",
      roles: ["CUSTOMER"],
    },
  });

  const selectedRoles = form.watch("roles");

  function toggleRole(role: "CUSTOMER" | "HOST") {
    const next = selectedRoles.includes(role)
      ? selectedRoles.filter((r) => r !== role)
      : [...selectedRoles, role];
    form.setValue("roles", next, { shouldValidate: true });
  }

  async function onSubmit(values: RegisterForm) {
    setSubmitError(null);
    try {
      await registerUser(values);
      toast.success("Tạo tài khoản thành công");
      router.replace("/me/profile?onboarding=1");
      router.refresh();
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === "USER_EMAIL_EXISTS" || err.fieldError("email")) {
          form.setError("email", {
            message: err.fieldError("email") ?? "Email đã được sử dụng.",
          });
          return;
        }
        if (err.status === 400 && err.details.length > 0) {
          err.details.forEach((d) => {
            if (d.field === "email" || d.field === "password" || d.field === "fullName") {
              form.setError(d.field as keyof RegisterForm, { message: d.message });
            }
          });
          if (
            err.details.every((d) =>
              ["email", "password", "fullName"].includes(d.field),
            )
          ) {
            return;
          }
        }
        setSubmitError(err);
      } else {
        setSubmitError(new ApiError(0, { code: "UNKNOWN_ERROR", message: "Lỗi không xác định." }));
      }
    }
  }

  const errors = form.formState.errors;
  const isSubmitting = form.formState.isSubmitting;

  return (
    <AppShell activePath="/register">
      <div className="py-6">
        <AuthCard
          title="Tạo tài khoản RentFlow"
          description="Đăng ký để thuê xe hoặc trở thành Host của hệ thống."
        >
          <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
            <AuthFormLayout
              errorBanner={
                submitError ? (
                  <ErrorBanner error={submitError} title="Không thể tạo tài khoản" />
                ) : null
              }
              footer={
                <p>
                  Đã có tài khoản?{" "}
                  <Link href="/login" className="font-semibold text-primary hover:underline">
                    Đăng nhập
                  </Link>
                </p>
              }
            >
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Họ và tên</label>
                <input
                  type="text"
                  autoComplete="name"
                  {...form.register("fullName")}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.fullName ? (
                  <p className="mt-1 text-xs text-red-700">{errors.fullName.message}</p>
                ) : null}
              </div>

              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Email</label>
                <input
                  type="email"
                  autoComplete="email"
                  {...form.register("email")}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.email ? (
                  <p className="mt-1 text-xs text-red-700">{errors.email.message}</p>
                ) : null}
              </div>

              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Mật khẩu</label>
                <input
                  type="password"
                  autoComplete="new-password"
                  {...form.register("password")}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.password ? (
                  <p className="mt-1 text-xs text-red-700">{errors.password.message}</p>
                ) : null}
              </div>

              <div>
                <p className="mb-2 text-sm font-semibold text-foreground">Vai trò</p>
                <div className="space-y-2">
                  {ROLE_OPTIONS.map((option) => {
                    const selected = selectedRoles.includes(option.value);
                    return (
                      <button
                        key={option.value}
                        type="button"
                        onClick={() => toggleRole(option.value)}
                        className={[
                          "block w-full rounded-lg border px-3 py-2 text-left text-sm transition-colors",
                          selected
                            ? "border-primary bg-primary/5 text-foreground"
                            : "border-border bg-background text-foreground hover:bg-accent",
                        ].join(" ")}
                        aria-pressed={selected}
                      >
                        <span className="font-semibold">{option.label}</span>
                        {option.hint ? (
                          <span className="mt-0.5 block text-xs text-muted-foreground">
                            {option.hint}
                          </span>
                        ) : null}
                      </button>
                    );
                  })}
                </div>
                {errors.roles ? (
                  <p className="mt-1 text-xs text-red-700">{errors.roles.message as string}</p>
                ) : null}
              </div>

              <button
                type="submit"
                disabled={isSubmitting}
                className="h-10 w-full rounded-full bg-primary text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60"
              >
                {isSubmitting ? "Đang tạo tài khoản..." : "Tạo tài khoản"}
              </button>
            </AuthFormLayout>
          </form>
        </AuthCard>
      </div>
    </AppShell>
  );
}
