"use client";

import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { SessionExpiredState, useAuthenticatedProfile } from "@/features/profile/use-authenticated-profile";
import { ChecklistCard, OnboardingHero, StatusPanel } from "@/features/onboarding/onboarding-components";
import { hostOnboardingSteps } from "@/features/onboarding/model";

export function HostOnboardingPageView() {
  const { profileQuery, isLoading, isGuest, loginHref } = useAuthenticatedProfile();

  if (isLoading) {
    return (
      <AppShell activePath="/onboarding/host">
        <PageSkeleton message="Đang tải onboarding chủ xe..." />
      </AppShell>
    );
  }

  if (isGuest) {
    return (
      <AppShell activePath="/onboarding/host">
        <SessionExpiredState loginHref={loginHref} />
      </AppShell>
    );
  }

  if (!profileQuery.data) {
    return (
      <AppShell activePath="/onboarding/host">
        <EmptyState title="Không tải được trạng thái tài khoản" />
      </AppShell>
    );
  }

  const profile = profileQuery.data;

  if (!profile.roles.includes("HOST")) {
    return (
      <AppShell activePath="/onboarding/host">
        <StatusPanel
          title="Tài khoản chưa có vai trò chủ xe"
          description="Trang này dành cho chủ xe. Vui lòng kiểm tra vai trò trong hồ sơ."
          status="BLOCKED"
          statusLabel="Cần kiểm tra"
          actionHref="/me/profile"
          actionLabel="Mở hồ sơ"
        />
      </AppShell>
    );
  }

  return (
    <AppShell activePath="/onboarding/host">
      <div className="space-y-6">
        <OnboardingHero
          eyebrow="Onboarding chủ xe"
          title="Chuẩn bị hồ sơ cho thuê xe"
          description="Hoàn tất các bước tài khoản trước khi quản lý xe, tạo listing và nhận booking từ khách thuê."
        />
        <div className="grid gap-6 lg:grid-cols-[1.4fr_0.8fr]">
          <ChecklistCard steps={hostOnboardingSteps(profile)} />
          <StatusPanel
            title="Khu vực Host"
            description="Sau khi tài khoản sẵn sàng, bạn có thể thêm xe, cấu hình listing và theo dõi booking trong dashboard."
            status={profile.emailVerified ? "ACTIVE" : "PENDING"}
            statusLabel={profile.emailVerified ? "Sẵn sàng" : "Cần xác minh email"}
            actionHref="/host/dashboard"
            actionLabel="Mở dashboard"
          />
        </div>
      </div>
    </AppShell>
  );
}
