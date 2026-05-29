import type { ProfileViewModel } from "@/features/profile/types";

const MOCK_PROFILE: ProfileViewModel = {
  id: "usr-customer",
  email: "minh.nguyen@rentflow.vn",
  emailVerified: true,
  roles: ["CUSTOMER"],
  fullName: "Minh Nguyen",
  phone: "0902001888",
  dateOfBirth: "1996-04-18",
  addressLine: "District 7, Ho Chi Minh City",
  driverVerificationStatus: "APPROVED",
};

export function getMockProfile(): ProfileViewModel {
  return { ...MOCK_PROFILE };
}
