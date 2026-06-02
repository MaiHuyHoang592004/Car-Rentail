import type { ProfileViewModel } from "@/features/profile/types";

export type DriverVerificationStatus = ProfileViewModel["driverVerificationStatus"];

export type OnboardingStepState = "complete" | "current" | "blocked" | "pending";

export type OnboardingStep = {
  id: string;
  title: string;
  description: string;
  state: OnboardingStepState;
  href: string;
  ctaLabel: string;
};

export function normalizeDriverVerificationStatus(raw: string): DriverVerificationStatus | null {
  switch (raw) {
    case "NOT_SUBMITTED":
    case "PENDING":
    case "APPROVED":
    case "REJECTED":
    case "EXPIRED":
      return raw;
    default:
      return null;
  }
}

export function driverStatusLabel(status: DriverVerificationStatus): string {
  switch (status) {
    case "NOT_SUBMITTED":
      return "Chưa gửi";
    case "PENDING":
      return "Đang chờ duyệt";
    case "APPROVED":
      return "Đã duyệt";
    case "REJECTED":
      return "Bị từ chối";
    case "EXPIRED":
      return "Hết hạn";
  }
}

export function driverStatusCta(status: DriverVerificationStatus): { href: string; label: string } {
  switch (status) {
    case "NOT_SUBMITTED":
      return { href: "/onboarding/customer/driver-license", label: "Gửi GPLX" };
    case "PENDING":
      return { href: "/onboarding/customer/driver-license/pending", label: "Xem trạng thái" };
    case "APPROVED":
      return { href: "/listings", label: "Tìm xe để đặt" };
    case "REJECTED":
    case "EXPIRED":
      return { href: "/onboarding/customer/driver-license/rejected", label: "Gửi lại GPLX" };
  }
}

export function customerOnboardingSteps(profile: ProfileViewModel): OnboardingStep[] {
  const emailDone = profile.emailVerified;
  const driverDone = profile.driverVerificationStatus === "APPROVED";
  const driverPending = profile.driverVerificationStatus === "PENDING";
  const driverNeedsResubmit =
    profile.driverVerificationStatus === "REJECTED" || profile.driverVerificationStatus === "EXPIRED";
  const driverCta = driverStatusCta(profile.driverVerificationStatus);

  return [
    {
      id: "email",
      title: "Xác minh email",
      description: emailDone
        ? "Email đã được xác minh. Bạn có thể tiếp tục hoàn tất hồ sơ."
        : "Xác minh email để RentFlow bảo vệ tài khoản và gửi thông báo booking.",
      state: emailDone ? "complete" : "current",
      href: "/onboarding/customer/email",
      ctaLabel: emailDone ? "Đã hoàn tất" : "Xác minh email",
    },
    {
      id: "driver-license",
      title: "Xác minh giấy phép lái xe",
      description: driverDone
        ? "GPLX đã được duyệt. Bạn đã đủ điều kiện đặt xe."
        : driverPending
          ? "Hồ sơ GPLX đang được đội ngũ RentFlow kiểm tra."
          : driverNeedsResubmit
            ? "GPLX cần được cập nhật trước khi bạn đặt xe."
            : "Gửi thông tin GPLX để RentFlow kiểm tra điều kiện thuê xe.",
      state: driverDone ? "complete" : driverPending ? "pending" : emailDone ? "current" : "blocked",
      href: driverCta.href,
      ctaLabel: driverCta.label,
    },
  ];
}

export function hostOnboardingSteps(profile: ProfileViewModel): OnboardingStep[] {
  return [
    {
      id: "email",
      title: "Xác minh email",
      description: profile.emailVerified
        ? "Email đã được xác minh. Bạn có thể quản lý xe và listing."
        : "Xác minh email để nhận thông báo booking và cập nhật vận hành.",
      state: profile.emailVerified ? "complete" : "current",
      href: "/onboarding/customer/email",
      ctaLabel: profile.emailVerified ? "Đã hoàn tất" : "Xác minh email",
    },
    {
      id: "host-dashboard",
      title: "Chuẩn bị hồ sơ chủ xe",
      description: "Thêm xe, tạo listing và quản lý lịch trống trong khu vực Host.",
      state: profile.emailVerified ? "current" : "blocked",
      href: "/host/dashboard",
      ctaLabel: "Mở bảng điều khiển Host",
    },
  ];
}

export function customerReadyToBook(profile: ProfileViewModel): boolean {
  return profile.emailVerified && profile.roles.includes("CUSTOMER") && profile.driverVerificationStatus === "APPROVED";
}
