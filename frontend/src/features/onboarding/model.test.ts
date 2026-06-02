import { describe, expect, it } from "vitest";

import { customerOnboardingSteps, normalizeDriverVerificationStatus } from "./model";
import type { ProfileViewModel } from "@/features/profile/types";

const baseProfile: ProfileViewModel = {
  id: "u-1",
  email: "u@example.com",
  emailVerified: true,
  roles: ["CUSTOMER"],
  fullName: "User",
  phone: "",
  dateOfBirth: "",
  addressLine: "",
  driverVerificationStatus: "NOT_SUBMITTED",
};

describe("onboarding model", () => {
  it("uses exact backend driver verification statuses", () => {
    expect(normalizeDriverVerificationStatus("APPROVED")).toBe("APPROVED");
    expect(normalizeDriverVerificationStatus("VERIFIED")).toBeNull();
  });

  it("routes pending customer GPLX status to pending onboarding page", () => {
    const steps = customerOnboardingSteps({
      ...baseProfile,
      driverVerificationStatus: "PENDING",
    });

    expect(steps[1]).toMatchObject({
      state: "pending",
      href: "/onboarding/customer/driver-license/pending",
      ctaLabel: "Xem trạng thái",
    });
  });
});
