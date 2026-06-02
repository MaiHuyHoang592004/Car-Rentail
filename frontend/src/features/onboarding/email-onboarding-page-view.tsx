"use client";

import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";

import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { resendVerificationEmail } from "@/features/profile/api";
import { SessionExpiredState, useAuthenticatedProfile } from "@/features/profile/use-authenticated-profile";
import { FriendlyError, OnboardingHero, StatusPanel } from "@/features/onboarding/onboarding-components";
import { ApiError } from "@/lib/api-error";

export function EmailOnboardingPageView() {
  const { profileQuery, isLoading, isGuest, loginHref } = useAuthenticatedProfile();
  const resendMutation = useMutation({
    mutationFn: resendVerificationEmail,
    onSuccess: () => toast.success("Đã gửi lại email xác minh. Vui lòng kiểm tra hộp thư."),
    onError: (error) => toast.error(friendlyResendError(error)),
  });

  if (isLoading) {
    return (
      <AppShell activePath="/onboarding/customer/email">
        <PageSkeleton message="Đang tải trạng thái email..." />
      </AppShell>
    );
  }

  if (isGuest) {
    return (
      <AppShell activePath="/onboarding/customer/email">
        <SessionExpiredState loginHref={loginHref} />
      </AppShell>
    );
  }

  if (!profileQuery.data) {
    return (
      <AppShell activePath="/onboarding/customer/email">
        <EmptyState title="Không tải được trạng thái email" />
      </AppShell>
    );
  }

  const profile = profileQuery.data;

  return (
    <AppShell activePath="/onboarding/customer/email">
      <div className="space-y-6">
        <OnboardingHero
          eyebrow="Xác minh email"
          title={profile.emailVerified ? "Email đã được xác minh" : "Kiểm tra hộp thư của bạn"}
          description="RentFlow dùng email đã xác minh để gửi thông báo booking, thanh toán và cập nhật bảo mật."
        />

        {resendMutation.isError ? (
          <FriendlyError message={friendlyResendError(resendMutation.error)} />
        ) : null}

        <StatusPanel
          title={profile.email}
          description={
            profile.emailVerified
              ? "Email này đã sẵn sàng cho các bước booking tiếp theo."
              : "Nếu chưa thấy email, hãy kiểm tra thư rác hoặc gửi lại email xác minh."
          }
          status={profile.emailVerified ? "APPROVED" : "PENDING"}
          statusLabel={profile.emailVerified ? "Đã xác minh" : "Chưa xác minh"}
          actionHref={profile.emailVerified ? "/onboarding/customer" : undefined}
          actionLabel={profile.emailVerified ? "Tiếp tục onboarding" : undefined}
        />

        {!profile.emailVerified ? (
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() => resendMutation.mutate()}
              disabled={resendMutation.isPending}
              className="rounded-full bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {resendMutation.isPending ? "Đang gửi..." : "Gửi lại email xác minh"}
            </button>
            <button
              type="button"
              onClick={() => profileQuery.refetch()}
              className="rounded-full border border-slate-300 bg-white px-5 py-2.5 text-sm font-semibold text-slate-800 transition-colors hover:bg-slate-50"
            >
              Tôi đã xác minh, kiểm tra lại
            </button>
          </div>
        ) : null}
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
  return "Không gửi được email xác minh lúc này. Vui lòng thử lại sau.";
}
