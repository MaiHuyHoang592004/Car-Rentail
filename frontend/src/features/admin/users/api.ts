import { api } from "@/lib/api-client";
import type {
  AdminUserFilterRole,
  AdminUserFilterStatus,
  AdminUserPage,
  AdminUserSummary,
} from "@/features/admin/users/types";

type RawPageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

type RawUserSummary = {
  id: string;
  email: string;
  roles: string[];
  fullName: string;
  status: string;
  driverVerificationStatus: string;
  createdAt: string;
  lastLoginAt: string | null;
};

function mapUser(raw: RawUserSummary): AdminUserSummary {
  return {
    id: raw.id,
    email: raw.email,
    roles: raw.roles,
    fullName: raw.fullName,
    status: raw.status,
    driverVerificationStatus: raw.driverVerificationStatus,
    createdAt: raw.createdAt,
    lastLoginAt: raw.lastLoginAt,
  };
}

export async function adminListUsers(
  filters: {
    status?: AdminUserFilterStatus;
    role?: AdminUserFilterRole;
    page?: number;
    size?: number;
  },
  signal?: AbortSignal,
): Promise<AdminUserPage> {
  const params = new URLSearchParams();
  if (filters.status && filters.status !== "ALL") {
    params.set("status", filters.status);
  }
  if (filters.role && filters.role !== "ALL") {
    params.set("role", filters.role);
  }
  params.set("page", String(filters.page ?? 0));
  params.set("size", String(filters.size ?? 20));

  const raw = await api.get<RawPageResponse<RawUserSummary>>(
    `/admin/users?${params.toString()}`,
    { signal },
  );
  return {
    users: raw.content.map(mapUser),
    page: raw.page,
    size: raw.size,
    totalElements: raw.totalElements,
    totalPages: raw.totalPages,
  };
}

export async function adminSuspendUser(userId: string): Promise<AdminUserSummary> {
  return mapUser(await api.post<RawUserSummary>(`/admin/users/${userId}/suspend`, {}));
}

export async function adminReactivateUser(userId: string): Promise<AdminUserSummary> {
  return mapUser(await api.post<RawUserSummary>(`/admin/users/${userId}/reactivate`, {}));
}
