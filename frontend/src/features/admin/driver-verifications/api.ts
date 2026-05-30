import { api } from "@/lib/api-client";

export type AdminDriverVerification = {
  id: string;
  customerId: string;
  status: string;
  licenseExpiryDate: string;
  documentFileId: string;
  documentPreviewUrl?: string | null;
  pendingAgeHours?: number | null;
  slaBreached?: boolean | null;
  reviewReason?: string | null;
  submittedAt: string;
};

type RawPage<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

export async function adminListDriverVerifications(status = "PENDING") {
  const params = new URLSearchParams();
  if (status !== "ALL") params.set("status", status);
  const raw = await api.get<RawPage<AdminDriverVerification>>(`/admin/driver-verifications?${params.toString()}`);
  return raw.content;
}

export async function adminApproveDriverVerification(id: string, reason = "Approved") {
  return api.post<AdminDriverVerification>(`/admin/driver-verifications/${id}/approve`, { reason });
}

export async function adminRejectDriverVerification(id: string, reason: string) {
  return api.post<AdminDriverVerification>(`/admin/driver-verifications/${id}/reject`, { reason });
}
