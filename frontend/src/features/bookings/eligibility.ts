import type { ApiError } from "@/lib/api-error";
import type { ProfileViewModel } from "@/features/profile/types";

export type BookingBlockerCode =
  | "EMAIL_NOT_VERIFIED"
  | "DRIVER_VERIFICATION_REQUIRED"
  | "DRIVER_VERIFICATION_PENDING"
  | "DRIVER_VERIFICATION_REJECTED"
  | "CUSTOMER_ROLE_REQUIRED";

export type BookingBlocker = {
  code: BookingBlockerCode;
  label: string;
  ctaHref: string;
  ctaLabel: string;
};

export type BookingEligibility = {
  canBook: boolean;
  blockers: BookingBlocker[];
  primaryCtaHref: string;
  primaryCtaLabel: string;
};

export type BookingErrorPresentation =
  | {
      kind: "blocker";
      blocker: BookingBlocker;
      message: string;
    }
  | {
      kind: "form";
      message: string;
    }
  | {
      kind: "overlap";
      message: string;
    };

const PROFILE_CTA_HREF = "/me/profile";
const EMAIL_CTA_HREF = "/onboarding/customer/email";
const DRIVER_CTA_HREF = "/onboarding/customer/driver-license";
const DRIVER_PENDING_CTA_HREF = "/onboarding/customer/driver-license/pending";
const DRIVER_REJECTED_CTA_HREF = "/onboarding/customer/driver-license/rejected";

function blocker(code: BookingBlockerCode): BookingBlocker {
  switch (code) {
    case "EMAIL_NOT_VERIFIED":
      return {
        code,
        label: "Xác minh email",
        ctaHref: EMAIL_CTA_HREF,
        ctaLabel: "Hoàn tất xác minh",
      };
    case "DRIVER_VERIFICATION_REQUIRED":
      return {
        code,
        label: "Xác minh giấy phép lái xe",
        ctaHref: DRIVER_CTA_HREF,
        ctaLabel: "Hoàn tất xác minh",
      };
    case "DRIVER_VERIFICATION_PENDING":
      return {
        code,
        label: "GPLX đang chờ duyệt",
        ctaHref: DRIVER_PENDING_CTA_HREF,
        ctaLabel: "Xem trạng thái xác minh",
      };
    case "DRIVER_VERIFICATION_REJECTED":
      return {
        code,
        label: "Cập nhật lại giấy phép lái xe",
        ctaHref: DRIVER_REJECTED_CTA_HREF,
        ctaLabel: "Cập nhật xác minh",
      };
    case "CUSTOMER_ROLE_REQUIRED":
      return {
        code,
        label: "Tài khoản cần có vai trò khách thuê",
        ctaHref: PROFILE_CTA_HREF,
        ctaLabel: "Xem hồ sơ",
      };
  }
}

export function deriveBookingEligibility(profile: Pick<
  ProfileViewModel,
  "emailVerified" | "roles" | "driverVerificationStatus"
>): BookingEligibility {
  const blockers: BookingBlocker[] = [];

  if (!profile.roles.includes("CUSTOMER")) {
    blockers.push(blocker("CUSTOMER_ROLE_REQUIRED"));
  }
  if (!profile.emailVerified) {
    blockers.push(blocker("EMAIL_NOT_VERIFIED"));
  }

  switch (profile.driverVerificationStatus) {
    case "NOT_SUBMITTED":
      blockers.push(blocker("DRIVER_VERIFICATION_REQUIRED"));
      break;
    case "PENDING":
      blockers.push(blocker("DRIVER_VERIFICATION_PENDING"));
      break;
    case "REJECTED":
    case "EXPIRED":
      blockers.push(blocker("DRIVER_VERIFICATION_REJECTED"));
      break;
    case "APPROVED":
      break;
  }

  return {
    canBook: blockers.length === 0,
    blockers,
    primaryCtaHref: blockers[0]?.ctaHref ?? PROFILE_CTA_HREF,
    primaryCtaLabel: blockers[0]?.ctaLabel ?? "Hoàn tất xác minh",
  };
}

export function mapBookingCreateError(error: ApiError): BookingErrorPresentation | null {
  switch (error.code) {
    case "EMAIL_NOT_VERIFIED":
      return {
        kind: "blocker",
        blocker: blocker("EMAIL_NOT_VERIFIED"),
        message: "Bạn cần xác minh email trước khi đặt xe.",
      };
    case "DRIVER_VERIFICATION_REQUIRED":
      return {
        kind: "blocker",
        blocker: blocker("DRIVER_VERIFICATION_REQUIRED"),
        message: "Bạn cần xác minh giấy phép lái xe trước khi đặt xe.",
      };
    case "DRIVER_VERIFICATION_PENDING":
      return {
        kind: "blocker",
        blocker: blocker("DRIVER_VERIFICATION_PENDING"),
        message: "GPLX của bạn đang chờ duyệt.",
      };
    case "DRIVER_VERIFICATION_REJECTED":
      return {
        kind: "blocker",
        blocker: blocker("DRIVER_VERIFICATION_REJECTED"),
        message: "GPLX chưa được duyệt. Vui lòng gửi lại thông tin.",
      };
    case "IDEMPOTENCY_KEY_REQUIRED":
      return {
        kind: "form",
        message: "Phiên đặt xe chưa hợp lệ. Vui lòng thử lại.",
      };
    case "LISTING_NOT_AVAILABLE":
      return {
        kind: "form",
        message: "Xe không còn trống trong khoảng thời gian này.",
      };
    case "RATE_LIMIT_EXCEEDED":
      return {
        kind: "form",
        message: "Bạn thao tác quá nhanh. Vui lòng thử lại sau.",
      };
    case "BOOKING_OVERLAP_CUSTOMER":
      return {
        kind: "overlap",
        message: "Bạn đã có booking trùng thời gian.",
      };
    default:
      return null;
  }
}
