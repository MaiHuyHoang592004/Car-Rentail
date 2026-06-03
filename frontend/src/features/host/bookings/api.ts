"use client";

import { api } from "@/lib/api-client";
import type {
  BookingDetailViewModel,
  BookingListFilterValue,
  BookingPolicySnapshot,
  BookingPriceSnapshot,
  BookingStatus,
  BookingSummaryViewModel,
} from "@/features/bookings/types";

type RawHostBookingResponse = {
  id: string;
  status: BookingStatus;
  listingId: string;
  listingTitle: string;
  customerId?: string;
  hostId?: string;
  pickupDate: string;
  returnDate: string;
  pickupLocation: string;
  returnLocation: string;
  holdExpiresAt: string | null;
  hostApprovalExpiresAt?: string | null;
  totalAmount: number | string;
  currency: string;
  priceSnapshot: unknown;
  policySnapshot: unknown;
  rejectionReason?: string | null;
  cancellationReason?: string | null;
  voidRetryRequired?: boolean;
  paymentRetryState?: string | null;
  paymentStatus?: string | null;
  voidRetryLastError?: string | null;
  voidRetryCount?: number | null;
  createdAt: string;
};

type RawHostBookingSummary = {
  id: string;
  status: BookingStatus;
  listingId: string;
  listingTitle: string;
  pickupDate: string;
  returnDate: string;
  holdExpiresAt: string | null;
  hostApprovalExpiresAt?: string | null;
  totalAmount: number | string;
  currency: string;
  voidRetryRequired?: boolean;
  paymentRetryState?: string | null;
  paymentStatus?: string | null;
  voidRetryLastError?: string | null;
  voidRetryCount?: number | null;
  createdAt: string;
};

type RawPageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type HostBookingPage = {
  content: BookingSummaryViewModel[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

function toNumber(value: number | string | null | undefined): number {
  if (value === null || value === undefined) return 0;
  if (typeof value === "number") return value;
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

const EMPTY_PRICE_SNAPSHOT: BookingPriceSnapshot = {
  rentalDays: 0,
  basePricePerDay: 0,
  baseAmount: 0,
  extraAmount: 0,
  totalAmount: 0,
  currency: "VND",
  extras: [],
};

const EMPTY_POLICY_SNAPSHOT: BookingPolicySnapshot = {
  cancellationPolicy: "FLEXIBLE",
  instantBook: false,
  dailyKmLimit: 0,
};

function parsePriceSnapshot(raw: unknown, fallbackCurrency: string): BookingPriceSnapshot {
  if (!raw || typeof raw !== "object") return { ...EMPTY_PRICE_SNAPSHOT, currency: fallbackCurrency };
  const obj = raw as Record<string, unknown>;
  const extrasRaw = Array.isArray(obj.extras) ? obj.extras : [];
  return {
    rentalDays: toNumber(obj.rentalDays as number | string),
    basePricePerDay: toNumber(obj.basePricePerDay as number | string),
    baseAmount: toNumber(obj.baseAmount as number | string),
    extraAmount: toNumber(obj.extraAmount as number | string),
    totalAmount: toNumber(obj.totalAmount as number | string),
    currency: typeof obj.currency === "string" ? obj.currency : fallbackCurrency,
    extras: extrasRaw.map((item) => {
      const extra = item as Record<string, unknown>;
      return {
        id: String(extra.id ?? extra.extraId ?? ""),
        name: String(extra.name ?? ""),
        quantity: toNumber(extra.quantity as number | string),
        unitPrice: toNumber(extra.unitPrice as number | string),
        totalPrice: toNumber(extra.totalPrice as number | string),
        currency: typeof extra.currency === "string" ? extra.currency : fallbackCurrency,
      };
    }),
  };
}

function parsePolicySnapshot(raw: unknown): BookingPolicySnapshot {
  if (!raw || typeof raw !== "object") return { ...EMPTY_POLICY_SNAPSHOT };
  const obj = raw as Record<string, unknown>;
  const policy = obj.cancellationPolicy;
  return {
    cancellationPolicy:
      policy === "FLEXIBLE" || policy === "MODERATE" || policy === "STRICT" ? policy : "FLEXIBLE",
    instantBook: Boolean(obj.instantBook),
    dailyKmLimit: toNumber(obj.dailyKmLimit as number | string),
  };
}

function mapSummary(raw: RawHostBookingSummary): BookingSummaryViewModel {
  return {
    id: raw.id,
    status: raw.status,
    listingId: raw.listingId,
    listingTitle: raw.listingTitle,
    createdAt: raw.createdAt,
    pickupDate: raw.pickupDate,
    returnDate: raw.returnDate,
    holdExpiresAt: raw.holdExpiresAt ?? undefined,
    hostApprovalExpiresAt: raw.hostApprovalExpiresAt ?? undefined,
    totalAmount: toNumber(raw.totalAmount),
    currency: raw.currency,
    voidRetryRequired: raw.voidRetryRequired ?? false,
    paymentRetryState: raw.paymentRetryState ?? undefined,
    paymentStatus: raw.paymentStatus ?? undefined,
    voidRetryLastError: raw.voidRetryLastError ?? undefined,
    voidRetryCount: raw.voidRetryCount ?? undefined,
  };
}

function mapDetail(raw: RawHostBookingResponse): BookingDetailViewModel {
  return {
    id: raw.id,
    status: raw.status,
    listingId: raw.listingId,
    listingTitle: raw.listingTitle,
    createdAt: raw.createdAt,
    customerId: raw.customerId,
    hostId: raw.hostId,
    pickupDate: raw.pickupDate,
    returnDate: raw.returnDate,
    pickupLocation: raw.pickupLocation,
    returnLocation: raw.returnLocation,
    holdExpiresAt: raw.holdExpiresAt ?? undefined,
    hostApprovalExpiresAt: raw.hostApprovalExpiresAt ?? undefined,
    totalAmount: toNumber(raw.totalAmount),
    currency: raw.currency,
    cancellationReason: raw.cancellationReason ?? undefined,
    rejectionReason: raw.rejectionReason ?? undefined,
    voidRetryRequired: raw.voidRetryRequired ?? false,
    paymentRetryState: raw.paymentRetryState ?? undefined,
    paymentStatus: raw.paymentStatus ?? undefined,
    voidRetryLastError: raw.voidRetryLastError ?? undefined,
    voidRetryCount: raw.voidRetryCount ?? undefined,
    priceSnapshot: parsePriceSnapshot(raw.priceSnapshot, raw.currency),
    policySnapshot: parsePolicySnapshot(raw.policySnapshot),
  };
}

export async function getHostBookings(
  params: {
    status: BookingListFilterValue;
    listingId?: string;
    page?: number;
    size?: number;
  },
  signal?: AbortSignal,
): Promise<HostBookingPage> {
  const search = new URLSearchParams();
  if (params.status !== "ALL") search.set("status", params.status);
  if (params.listingId && params.listingId !== "ALL") search.set("listingId", params.listingId);
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 20));
  const raw = await api.get<RawPageResponse<RawHostBookingSummary>>(
    `/host/bookings?${search.toString()}`,
    { signal },
  );
  return {
    content: raw.content.map(mapSummary),
    page: raw.page,
    size: raw.size,
    totalElements: raw.totalElements,
    totalPages: raw.totalPages,
  };
}

export async function getHostBookingById(id: string): Promise<BookingDetailViewModel> {
  const raw = await api.get<RawHostBookingResponse>(`/host/bookings/${id}`);
  return mapDetail(raw);
}

export async function approveHostBooking(id: string, idempotencyKey: string): Promise<BookingDetailViewModel> {
  const raw = await api.post<RawHostBookingResponse>(`/host/bookings/${id}/approve`, undefined, {
    idempotencyKey,
  });
  return mapDetail(raw);
}

export async function rejectHostBooking(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<BookingDetailViewModel> {
  const raw = await api.post<RawHostBookingResponse>(
    `/host/bookings/${id}/reject`,
    { reason },
    { idempotencyKey },
  );
  return mapDetail(raw);
}
