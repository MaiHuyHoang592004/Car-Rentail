"use client";

import Link from "next/link";

import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { SessionExpiredState, useAuthenticatedProfile } from "@/features/profile/use-authenticated-profile";
import { ChecklistCard, OnboardingHero, StatusPanel } from "@/features/onboarding/onboarding-components";
import { customerOnboardingSteps, customerReadyToBook, driverStatusLabel } from "@/features/onboarding/model";

export function CustomerOnboardingPageView() {
  const { profileQuery, isLoading, isGuest, loginHref } = useAuthenticatedProfile();

  if (isLoading) {
    return (
      <AppShell activePath="/onboarding/customer">
        <PageSkeleton message="Đang tải trạng thái onboarding..." />
      </AppShell>
    );
  }

  if (isGuest) {
    return (
      <AppShell activePath="/onboarding/customer">
        <SessionExpiredState loginHref={loginHref} />
      </AppShell>
    );
  }

  if (!profileQuery.data) {
    return (
      <AppShell activePath="/onboarding/customer">
        <EmptyState title="Không tải được trạng thái tài khoản" />
      </AppShell>
    );
  }

  const profile = profileQuery.data;

  if (!profile.roles.includes("CUSTOMER")) {
    return (
      <AppShell activePath="/onboarding/customer">
        <StatusPanel
          title="Tài khoản chưa có vai trò khách thuê"
          description="Trang này dành cho khách thuê xe. Bạn có thể kiểm tra vai trò hiện tại trong hồ sơ."
          status="BLOCKED"
          statusLabel="Cần kiểm tra"
          actionHref="/me/profile"
          actionLabel="Mở hồ sơ"
        />
      </AppShell>
    );
  }

  const ready = customerReadyToBook(profile);

  return (
    <AppShell activePath="/onboarding/customer">
      <div className="space-y-6">
        <OnboardingHero
          eyebrow="Onboarding khách thuê"
          title={ready ? "Bạn đã sẵn sàng đặt xe" : "Hoàn tất xác minh để đặt xe"}
          description="RentFlow kiểm tra email và GPLX trước khi giữ xe, giúp booking an toàn và minh bạch hơn."
        >
          <div className="rounded-2xl border border-white/70 bg-white/80 p-4 text-sm shadow-sm">
            <p className="font-semibold text-slate-950">{profile.fullName}</p>
            <p className="mt-1 text-slate-600">{profile.email}</p>
          </div>
        </OnboardingHero>

        <div className="grid gap-6 lg:grid-cols-[1.4fr_0.8fr]">
          <ChecklistCard steps={customerOnboardingSteps(profile)} />
          <StatusPanel
            title={ready ? "Có thể đặt xe" : "Chưa thể đặt xe"}
            description={
              ready
                ? "Bạn đã đủ điều kiện gửi yêu cầu booking và giữ xe trong 15 phút sau khi tạo booking."
                : "Hoàn tất các bước còn thiếu để nút đặt xe được mở trên trang booking."
            }
            status={profile.driverVerificationStatus}
            statusLabel={`GPLX: ${driverStatusLabel(profile.driverVerificationStatus)}`}
            actionHref={ready ? "/listings" : "/me/profile"}
            actionLabel={ready ? "Tìm xe" : "Xem hồ sơ"}
          />
        </div>

        <Link href="/me/profile" className="inline-flex text-sm font-semibold text-blue-700 hover:underline">
          Quay lại hồ sơ
        </Link>
      </div>
    </AppShell>
  );
}
