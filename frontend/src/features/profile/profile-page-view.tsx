"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { PageHeader } from "@/components/rentflow/page-header";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { StatusBadge } from "@/components/rentflow/status-badge";
import type {
  ChangePasswordFormErrors,
  ChangePasswordFormState,
  ProfileFormErrors,
  ProfileFormState,
  ProfileViewModel,
} from "@/features/profile/types";
import { changePassword, resendVerificationEmail, updateProfile } from "@/features/profile/api";
import { AccountCompletionCard } from "@/features/onboarding/account-completion-card";
import { SessionExpiredState, useAuthenticatedProfile } from "@/features/profile/use-authenticated-profile";
import { ApiError } from "@/lib/api-error";

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

function validateChangePasswordForm(form: ChangePasswordFormState): ChangePasswordFormErrors {
  const errors: ChangePasswordFormErrors = {};

  if (!form.currentPassword) {
    errors.currentPassword = "Vui lòng nhập mật khẩu hiện tại.";
  }
  if (!form.newPassword) {
    errors.newPassword = "Vui lòng nhập mật khẩu mới.";
  } else if (form.newPassword.length < 8) {
    errors.newPassword = "Mật khẩu mới phải có ít nhất 8 ký tự.";
  }
  if (!form.confirmPassword) {
    errors.confirmPassword = "Vui lòng xác nhận mật khẩu mới.";
  } else if (form.confirmPassword !== form.newPassword) {
    errors.confirmPassword = "Mật khẩu xác nhận không khớp.";
  }

  return errors;
}

export function ProfilePageView() {
  const { profileQuery, isLoading, isGuest, loginHref } = useAuthenticatedProfile();

  if (isLoading) {
    return (
      <AppShell activePath="/me/profile">
        <PageSkeleton message="Đang tải hồ sơ..." />
      </AppShell>
    );
  }

  if (isGuest) {
    return (
      <AppShell activePath="/me/profile">
        <SessionExpiredState loginHref={loginHref} />
      </AppShell>
    );
  }

  if (!profileQuery.data) {
    return (
      <AppShell activePath="/me/profile">
        <EmptyState title="Không tải được hồ sơ" />
      </AppShell>
    );
  }

  return <ProfileContent profile={profileQuery.data} />;
}

function ProfileContent({ profile }: { profile: ProfileViewModel }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<ProfileFormState>({
    fullName: profile.fullName,
    phone: profile.phone,
    dateOfBirth: profile.dateOfBirth,
    addressLine: profile.addressLine,
  });
  const [errors, setErrors] = useState<ProfileFormErrors>({});
  const [banner, setBanner] = useState<string>("");
  const [passwordForm, setPasswordForm] = useState<ChangePasswordFormState>({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });
  const [passwordErrors, setPasswordErrors] = useState<ChangePasswordFormErrors>({});

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
  const { mutate: resendVerification, isPending: resendingVerification } = useMutation({
    mutationFn: resendVerificationEmail,
    onSuccess: () => {
      toast.success("Đã gửi lại email xác minh. Vui lòng kiểm tra hộp thư của bạn.");
    },
    onError: (error) => {
      toast.error(friendlyResendError(error));
    },
  });
  const { mutate: doChangePassword, isPending: changingPassword } = useMutation({
    mutationFn: changePassword,
    onSuccess: () => {
      setPasswordForm({
        currentPassword: "",
        newPassword: "",
        confirmPassword: "",
      });
      setPasswordErrors({});
      toast.success("Đã đổi mật khẩu.");
    },
    onError: (error) => {
      const message = friendlyChangePasswordError(error);
      setPasswordErrors((prev) => ({ ...prev, form: message }));
      toast.error(message);
    },
  });

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

  function updatePasswordField(field: keyof ChangePasswordFormState, value: string) {
    setPasswordForm((prev) => ({ ...prev, [field]: value }));
    setPasswordErrors((prev) => ({ ...prev, [field]: undefined, form: undefined }));
  }

  function handleChangePassword(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateChangePasswordForm(passwordForm);
    setPasswordErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    doChangePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
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

        <AccountCompletionCard profile={profile} />

        {!profile.emailVerified ? (
          <section className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-900">
            <p className="text-sm font-semibold">Email của bạn chưa được xác minh</p>
            <p className="mt-1 text-sm">
              Bạn cần xác minh email trước khi tạo booking hoặc thực hiện thanh toán.
            </p>
            <button
              type="button"
              onClick={() => resendVerification()}
              disabled={resendingVerification}
              className="mt-3 rounded-full bg-amber-700 px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {resendingVerification ? "Đang gửi..." : "Gửi lại email xác minh"}
            </button>
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

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <h2 className="text-lg font-bold text-foreground">Đổi mật khẩu</h2>
          <form onSubmit={handleChangePassword} noValidate className="mt-4 space-y-4">
            <div>
              <label className="mb-1 block text-sm font-semibold text-foreground">Mật khẩu hiện tại</label>
              <input
                type="password"
                aria-label="Mật khẩu hiện tại"
                value={passwordForm.currentPassword}
                onChange={(event) => updatePasswordField("currentPassword", event.target.value)}
                className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
              />
              {passwordErrors.currentPassword ? (
                <p className="mt-1 text-xs text-rose-700">{passwordErrors.currentPassword}</p>
              ) : null}
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Mật khẩu mới</label>
                <input
                  type="password"
                  aria-label="Mật khẩu mới"
                  value={passwordForm.newPassword}
                  onChange={(event) => updatePasswordField("newPassword", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {passwordErrors.newPassword ? (
                  <p className="mt-1 text-xs text-rose-700">{passwordErrors.newPassword}</p>
                ) : null}
              </div>

              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Xác nhận mật khẩu mới</label>
                <input
                  type="password"
                  aria-label="Xác nhận mật khẩu mới"
                  value={passwordForm.confirmPassword}
                  onChange={(event) => updatePasswordField("confirmPassword", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {passwordErrors.confirmPassword ? (
                  <p className="mt-1 text-xs text-rose-700">{passwordErrors.confirmPassword}</p>
                ) : null}
              </div>
            </div>

            {passwordErrors.form ? (
              <p className="text-sm text-rose-700">{passwordErrors.form}</p>
            ) : null}

            <button
              type="submit"
              disabled={changingPassword}
              className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {changingPassword ? "Đang đổi mật khẩu..." : "Đổi mật khẩu"}
            </button>
          </form>
        </section>
      </div>
    </AppShell>
  );
}

function friendlyResendError(error: unknown): string {
  if (error instanceof ApiError && (error.status === 401 || error.code === "AUTH_INVALID_CREDENTIALS" || error.code === "AUTH_TOKEN_EXPIRED")) {
    return "Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.";
  }
  if (error instanceof ApiError && (error.status === 403 || error.code === "ACCESS_DENIED")) {
    return "Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.";
  }
  if (error instanceof ApiError && error.code === "EMAIL_DELIVERY_FAILED") {
    return "Chưa gửi được email xác minh. Vui lòng thử lại sau.";
  }
  return "Không thể gửi lại email xác minh. Vui lòng thử lại.";
}

function friendlyChangePasswordError(error: unknown): string {
  if (error instanceof ApiError && (error.status === 401 || error.code === "AUTH_INVALID_CREDENTIALS")) {
    return "Mật khẩu hiện tại không đúng.";
  }
  if (error instanceof ApiError && error.status === 403) {
    return "Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.";
  }
  return "Không thể đổi mật khẩu. Vui lòng thử lại.";
}
