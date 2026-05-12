export type AuthRoleOption = "CUSTOMER" | "HOST";

export type AuthFormState = {
  email: string;
  password: string;
  fullName: string;
  roles: AuthRoleOption[];
};

export type AuthFormErrors = Partial<Record<keyof AuthFormState, string>>;

export type GuestIntentRedirect = {
  nextPath: string;
};
