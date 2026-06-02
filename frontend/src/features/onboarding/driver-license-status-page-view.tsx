"use client";

import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { SessionExpiredState, useAuthenticatedProfile } from "@/features/profile/use-authenticated-profile";
import { OnboardingHero, StatusPanel } from "@/features/onboarding/onboarding-components";
import { driverStatusLabel } from "@/features/onboarding/model";

export function DriverLicensePendingPageView() {
  const { profileQuery: query, isLoading, isGuest, loginHref } = useAuthenticatedProfile();

  if (isLoading) {
    return (
      <AppShell activePath="/onboarding/customer/driver-license/pending">
        <PageSkeleton message="Đang tải trạng thái GPLX..." />
      </AppShell>
    );
  }

  if (isGuest) {
    return (
      <AppShell activePath="/onboarding/customer/driver-license/pending">
        <SessionExpiredState loginHref={loginHref} />
      </AppShell>
    );
  }

  if (!query.data) {
    return (
      <AppShell activePath="/onboarding/customer/driver-license/pending">
        <EmptyState title="Không tải được trạng thái GPLX" />
      </AppShell>
    );
  }

  const profile = query.data;
  const approved = profile.driverVerificationStatus === "APPROVED";

  return (
    <AppShell activePath="/onboarding/customer/driver-license/pending">
      <div className="space-y-6">
        <OnboardingHero
          eyebrow="GPLX đang chờ duyệt"
          title={approved ? "GPLX đã được duyệt" : "Đội ngũ RentFlow đang kiểm tra"}
          description="Bạn có thể quay lại trang này để cập nhật trạng thái xét duyệt GPLX."
        />
        <StatusPanel
          title={approved ? "Bạn đã sẵn sàng đặt xe" : "Hồ sơ đang được duyệt"}
          description={
            approved
              ? "GPLX đã được duyệt. Bạn có thể quay lại danh sách xe và tạo booking."
              : "Trong thời gian chờ, bạn vẫn có thể xem listing và ước tính giá nhưng chưa thể gửi booking."
          }
          status={profile.driverVerificationStatus}
          statusLabel={driverStatusLabel(profile.driverVerificationStatus)}
          actionHref={approved ? "/listings" : "/onboarding/customer"}
          actionLabel={approved ? "Tìm xe" : "Quay lại checklist"}
        />
        <button
          type="button"
          onClick={() => query.refetch()}
          className="rounded-full border border-slate-300 bg-white px-5 py-2.5 text-sm font-semibold text-slate-800 transition-colors hover:bg-slate-50"
        >
          Kiểm tra lại trạng thái
        </button>
      </div>
    </AppShell>
  );
}

export function DriverLicenseRejectedPageView() {
  const { profileQuery: query, isLoading, isGuest, loginHref } = useAuthenticatedProfile();

  if (isLoading) {
    return (
      <AppShell activePath="/onboarding/customer/driver-license/rejected">
        <PageSkeleton message="Đang tải trạng thái GPLX..." />
      </AppShell>
    );
  }

  if (isGuest) {
    return (
      <AppShell activePath="/onboarding/customer/driver-license/rejected">
        <SessionExpiredState loginHref={loginHref} />
      </AppShell>
    );
  }

  if (!query.data) {
    return (
      <AppShell activePath="/onboarding/customer/driver-license/rejected">
        <EmptyState title="Không tải được trạng thái GPLX" />
      </AppShell>
    );
  }

  const profile = query.data;

  return (
    <AppShell activePath="/onboarding/customer/driver-license/rejected">
      <div className="space-y-6">
        <OnboardingHero
          eyebrow="Cần cập nhật GPLX"
          title={profile.driverVerificationStatus === "EXPIRED" ? "GPLX đã hết hạn" : "GPLX chưa được duyệt"}
          description="Vui lòng gửi lại thông tin GPLX rõ ràng và còn hiệu lực để RentFlow kiểm tra lại."
        />
        <StatusPanel
          title="Gửi lại hồ sơ xác minh"
          description="Sau khi gửi lại, trạng thái sẽ chuyển sang đang chờ duyệt và bạn có thể theo dõi trong onboarding."
          status={profile.driverVerificationStatus}
          statusLabel={driverStatusLabel(profile.driverVerificationStatus)}
          actionHref="/onboarding/customer/driver-license"
          actionLabel="Gửi lại GPLX"
        />
      </div>
    </AppShell>
  );
}
