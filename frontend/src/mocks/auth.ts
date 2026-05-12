import type { AuthRoleOption } from "@/features/auth/types";

export const AUTH_ROLE_OPTIONS: { value: AuthRoleOption; label: string }[] = [
  { value: "CUSTOMER", label: "Customer" },
  { value: "HOST", label: "Host" },
];

export const AUTH_DEMO_ERRORS = {
  invalidCredentialsEmail: "wrong@rentflow.vn",
  duplicateEmail: "exists@rentflow.vn",
} as const;
