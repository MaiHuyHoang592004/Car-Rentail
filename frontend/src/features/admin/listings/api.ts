import { api } from "@/lib/api-client";
import { ApiError } from "@/lib/api-error";
import type {
  AdminListingDetail,
  AdminListingFilterValue,
  AdminListingPage,
  AdminListingStatus,
  AdminListingSummary,
} from "@/features/admin/listings/types";

/* ------------------------------------------------------------------ */
/*  Raw backend response types                                        */
/* ------------------------------------------------------------------ */

type RawPageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

type RawListingSummary = {
  id: string;
  title: string;
  city: string;
  status: AdminListingStatus;
  basePricePerDay: number | string;
  currency: string;
  createdAt: string;
};

type RawListingDetail = {
  listing: {
    id: string;
    vehicleId: string;
    title: string;
    description: string;
    city: string;
    address: string;
    basePricePerDay: number | string;
    currency: string;
    dailyKmLimit: number;
    instantBook: boolean;
    cancellationPolicy: "FLEXIBLE" | "MODERATE" | "STRICT";
    status: AdminListingStatus;
    createdAt: string;
  };
  host: { id: string; fullName: string; email: string } | null;
  bookingSummary: { activeBookings: number };
};

/* ------------------------------------------------------------------ */
/*  Helpers                                                           */
/* ------------------------------------------------------------------ */

function toNumber(value: number | string | null | undefined): number {
  if (value === null || value === undefined) return 0;
  if (typeof value === "number") return value;
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function mapSummary(raw: RawListingSummary): AdminListingSummary {
  return {
    id: raw.id,
    title: raw.title,
    city: raw.city,
    status: raw.status,
    basePricePerDay: toNumber(raw.basePricePerDay),
    currency: raw.currency,
    createdAt: raw.createdAt,
  };
}

function mapDetail(raw: RawListingDetail): AdminListingDetail {
  return {
    listing: {
      id: raw.listing.id,
      vehicleId: raw.listing.vehicleId,
      title: raw.listing.title,
      description: raw.listing.description,
      city: raw.listing.city,
      address: raw.listing.address,
      basePricePerDay: toNumber(raw.listing.basePricePerDay),
      currency: raw.listing.currency,
      dailyKmLimit: raw.listing.dailyKmLimit,
      instantBook: raw.listing.instantBook,
      cancellationPolicy: raw.listing.cancellationPolicy,
      status: raw.listing.status,
      createdAt: raw.listing.createdAt,
    },
    host: raw.host,
    bookingSummary: raw.bookingSummary,
  };
}

/* ------------------------------------------------------------------ */
/*  API functions                                                     */
/* ------------------------------------------------------------------ */

export async function adminListListings(
  filters: {
    status?: AdminListingFilterValue;
    hostId?: string;
    city?: string;
    page?: number;
    size?: number;
  },
  signal?: AbortSignal,
): Promise<AdminListingPage> {
  const params = new URLSearchParams();
  if (filters.status && filters.status !== "ALL") {
    params.set("status", filters.status);
  }
  if (filters.hostId) params.set("hostId", filters.hostId);
  if (filters.city) params.set("city", filters.city);
  params.set("page", String(filters.page ?? 0));
  params.set("size", String(filters.size ?? 20));

  const raw = await api.get<RawPageResponse<RawListingSummary>>(
    `/admin/listings?${params.toString()}`,
    { signal },
  );
  return {
    listings: raw.content.map(mapSummary),
    page: raw.page,
    size: raw.size,
    totalElements: raw.totalElements,
    totalPages: raw.totalPages,
  };
}

export async function adminGetListingDetail(
  listingId: string,
  signal?: AbortSignal,
): Promise<AdminListingDetail> {
  const raw = await api.get<RawListingDetail>(
    `/admin/listings/${listingId}`,
    { signal },
  );
  return mapDetail(raw);
}

async function adminPostActionAndRefreshDetail(
  listingId: string,
  path: string,
  body?: unknown,
): Promise<AdminListingDetail> {
  await api.post(path, body);
  return adminGetListingDetail(listingId);
}

export async function adminApproveListing(
  listingId: string,
): Promise<AdminListingDetail> {
  return adminPostActionAndRefreshDetail(
    listingId,
    `/admin/listings/${listingId}/approve`,
  );
}

export async function adminRejectListing(
  listingId: string,
  reason: string,
): Promise<AdminListingDetail> {
  return adminPostActionAndRefreshDetail(
    listingId,
    `/admin/listings/${listingId}/reject`,
    { reason },
  );
}

export async function adminSuspendListing(
  listingId: string,
  reason: string,
): Promise<AdminListingDetail> {
  return adminPostActionAndRefreshDetail(
    listingId,
    `/admin/listings/${listingId}/suspend`,
    { reason },
  );
}

export async function adminReactivateListing(
  listingId: string,
): Promise<AdminListingDetail> {
  return adminPostActionAndRefreshDetail(
    listingId,
    `/admin/listings/${listingId}/reactivate`,
  );
}

export class AdminListingActionError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly apiError?: ApiError,
  ) {
    super(message);
    this.name = "AdminListingActionError";
  }
}

function handleActionError(err: unknown): never {
  if (err instanceof ApiError) {
    throw new AdminListingActionError(err.code, err.message, err);
  }
  if (err instanceof Error) {
    throw new AdminListingActionError("UNKNOWN", err.message);
  }
  throw new AdminListingActionError("UNKNOWN", "An unexpected error occurred");
}

export async function adminApproveListingSafe(
  listingId: string,
): Promise<AdminListingDetail> {
  try {
    return await adminApproveListing(listingId);
  } catch (err) {
    handleActionError(err);
  }
}

export async function adminRejectListingSafe(
  listingId: string,
  reason: string,
): Promise<AdminListingDetail> {
  try {
    return await adminRejectListing(listingId, reason);
  } catch (err) {
    handleActionError(err);
  }
}

export async function adminSuspendListingSafe(
  listingId: string,
  reason: string,
): Promise<AdminListingDetail> {
  try {
    return await adminSuspendListing(listingId, reason);
  } catch (err) {
    handleActionError(err);
  }
}

export async function adminReactivateListingSafe(
  listingId: string,
): Promise<AdminListingDetail> {
  try {
    return await adminReactivateListing(listingId);
  } catch (err) {
    handleActionError(err);
  }
}
