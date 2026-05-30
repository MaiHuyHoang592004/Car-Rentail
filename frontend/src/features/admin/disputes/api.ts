import { api } from "@/lib/api-client";

export type AdminDisputeSummary = {
  id: string;
  bookingId: string;
  customerId: string;
  status: string;
  category: string;
  reason: string;
  context?: string | null;
  attachmentFileIds: string[];
  resolutionNote?: string | null;
  refundAction?: string | null;
  refundPaymentId?: string | null;
  refundAmount?: number | null;
  createdAt: string;
};

export type AdminDisputeDetail = {
  dispute: AdminDisputeSummary;
  booking: {
    id: string;
    status: string;
    listingId: string;
    customerId: string;
    hostId: string;
    pickupDate: string;
    returnDate: string;
  };
  payment: null | {
    id: string;
    status: string;
    provider: string;
    authorizedAmount: number | string;
    capturedAmount: number | string;
    refundedAmount: number | string;
    currency: string;
    voidRetryRequired: boolean;
    providerStatus?: string | null;
  };
  timeline: { id: string; eventType: string; actorType: string; payload?: string | null; createdAt: string }[];
};

type RawPage<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

export async function adminListDisputes(status = "OPEN") {
  const params = new URLSearchParams();
  if (status !== "ALL") params.set("status", status);
  const raw = await api.get<RawPage<AdminDisputeSummary>>(`/admin/disputes?${params.toString()}`);
  return raw.content;
}

export async function adminGetDispute(id: string): Promise<AdminDisputeDetail> {
  return api.get<AdminDisputeDetail>(`/admin/disputes/${id}`);
}

export async function adminResolveDispute(
  id: string,
  input: { resolutionNote: string; refundAction?: string; paymentId?: string; refundAmount?: number },
) {
  return api.post<AdminDisputeSummary>(`/admin/disputes/${id}/resolve`, input);
}
