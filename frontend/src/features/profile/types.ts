export type ProfileViewModel = {
  id: string;
  email: string;
  roles: ("CUSTOMER" | "HOST" | "ADMIN")[];
  fullName: string;
  phone: string;
  dateOfBirth: string;
  addressLine: string;
  driverVerificationStatus: "NOT_SUBMITTED" | "PENDING" | "APPROVED" | "REJECTED" | "EXPIRED";
};

export type ProfileFormState = {
  fullName: string;
  phone: string;
  dateOfBirth: string;
  addressLine: string;
};

export type ProfileFormErrors = Partial<Record<keyof ProfileFormState | "form", string>>;
