export type AdminUserStatus = "ACTIVE" | "SUSPENDED" | "DELETED";
export type AdminUserRole = "CUSTOMER" | "HOST" | "ADMIN";

export type AdminUserFilterStatus = "ALL" | AdminUserStatus;
export type AdminUserFilterRole = "ALL" | AdminUserRole;

export const ADMIN_USER_STATUS_FILTERS: AdminUserFilterStatus[] = ["ALL", "ACTIVE", "SUSPENDED", "DELETED"];
export const ADMIN_USER_ROLE_FILTERS: AdminUserFilterRole[] = ["ALL", "CUSTOMER", "HOST", "ADMIN"];

export type AdminUserSummary = {
  id: string;
  email: string;
  roles: string[];
  fullName: string;
  status: string;
  driverVerificationStatus: string;
  createdAt: string;
  lastLoginAt: string | null;
};

export type AdminUserPage = {
  users: AdminUserSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};