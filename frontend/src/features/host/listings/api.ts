import { api } from "@/lib/api-client";
import type {
  HostListingExtraViewModel,
  HostListingViewModel,
  HostListingStatus,
} from "@/features/host/types";
import { ApiError } from "@/lib/api-error";

export type HostListingFilterValue = "ALL" | HostListingStatus;

export const HOST_LISTING_STATUS_FILTERS: HostListingFilterValue[] = [
  "ALL",
  "DRAFT",
  "PENDING_APPROVAL",
  "ACTIVE",
  "SUSPENDED",
  "ARCHIVED",
];

type RawListingStatus =
  | "DRAFT"
  | "PENDING_APPROVAL"
  | "ACTIVE"
  | "SUSPENDED"
  | "ARCHIVED";

type RawVehicleSummary = {
  category: string;
  make: string;
  model: string;
  year: number;
  transmission: string;
  fuelType: string;
  seats: number;
  status: string;
};

type RawListingSummary = {
  id: string;
  title: string;
  city: string;
  status: RawListingStatus;
  basePricePerDay: number | string;
  currency: string;
  createdAt: string;
};

type RawListingDetail = {
  id: string;
  vehicleId: string;
  hostId: string;
  title: string;
  description: string;
  city: string;
  address: string;
  basePricePerDay: number | string;
  currency: string;
  dailyKmLimit: number;
  instantBook: boolean;
  cancellationPolicy: "FLEXIBLE" | "MODERATE" | "STRICT";
  status: RawListingStatus;
  suspensionReason?: string | null;
  suspensionSource?: string | null;
  suspensionUntil?: string | null;
  vehicleSummary: RawVehicleSummary | null;
  extras?: RawExtra[];
  createdAt: string;
  updatedAt: string | null;
};

type RawExtra = {
  id: string;
  name: string;
  pricingType: "PER_DAY" | "PER_TRIP";
  price: number | string;
  active: boolean;
};

type RawPageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

function toNumber(value: number | string | null | undefined): number {
  if (value === null || value === undefined) return 0;
  if (typeof value === "number") return value;
  const parsed = Number.parseFloat(String(value));
  return Number.isFinite(parsed) ? parsed : 0;
}

function mapVehicleLabel(vehicle: RawVehicleSummary | null): string {
  if (!vehicle) return "Unknown vehicle";
  return `${vehicle.make} ${vehicle.model} (${vehicle.year})`;
}

function mapExtra(raw: RawExtra): HostListingExtraViewModel {
  return {
    id: raw.id,
    name: raw.name,
    pricingType: raw.pricingType,
    price: toNumber(raw.price),
    active: raw.active,
  };
}

export function mapListingSummary(raw: RawListingSummary): HostListingViewModel {
  return {
    id: raw.id,
    vehicleId: "", // not in summary response
    vehicleLabel: "", // not in summary response
    title: raw.title,
    description: "",
    city: raw.city,
    address: "",
    basePricePerDay: toNumber(raw.basePricePerDay),
    currency: "VND" as const,
    dailyKmLimit: 0,
    instantBook: false,
    cancellationPolicy: "FLEXIBLE" as const,
    status: raw.status,
    suspensionReason: null,
    suspensionSource: null,
    suspensionUntil: null,
    extras: [],
  };
}

export function mapListingDetail(raw: RawListingDetail): HostListingViewModel {
  return {
    id: raw.id,
    vehicleId: raw.vehicleId,
    vehicleLabel: mapVehicleLabel(raw.vehicleSummary),
    title: raw.title,
    description: raw.description,
    city: raw.city,
    address: raw.address,
    basePricePerDay: toNumber(raw.basePricePerDay),
    currency: "VND" as const,
    dailyKmLimit: raw.dailyKmLimit,
    instantBook: raw.instantBook,
    cancellationPolicy: raw.cancellationPolicy,
    status: raw.status,
    suspensionReason: raw.suspensionReason ?? null,
    suspensionSource: raw.suspensionSource ?? null,
    suspensionUntil: raw.suspensionUntil ?? null,
    extras: (raw.extras ?? []).map(mapExtra),
  };
}

export async function getHostListings(
  status?: HostListingFilterValue,
  page = 0,
  size = 50,
): Promise<{ listings: HostListingViewModel[]; totalElements: number }> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status && status !== "ALL") {
    params.set("status", status);
  }

  const raw = await api.get<RawPageResponse<RawListingSummary>>(
    `/host/listings?${params}`,
  );
  return {
    listings: raw.content.map(mapListingSummary),
    totalElements: raw.totalElements,
  };
}

export async function getHostListingById(
  id: string,
): Promise<HostListingViewModel> {
  const raw = await api.get<RawListingDetail>(`/host/listings/${id}`);
  return mapListingDetail(raw);
}

export async function submitListing(id: string): Promise<HostListingViewModel> {
  const raw = await api.post<RawListingDetail>(`/host/listings/${id}/submit`);
  return mapListingDetail(raw);
}

export async function archiveListing(
  id: string,
): Promise<HostListingViewModel> {
  const raw = await api.post<RawListingDetail>(`/host/listings/${id}/archive`);
  return mapListingDetail(raw);
}

export async function reactivateListing(
  id: string,
): Promise<HostListingViewModel> {
  const raw = await api.post<RawListingDetail>(
    `/host/listings/${id}/reactivate`,
  );
  return mapListingDetail(raw);
}

export async function resumeListing(
  id: string,
): Promise<HostListingViewModel> {
  const raw = await api.post<RawListingDetail>(`/host/listings/${id}/resume`);
  return mapListingDetail(raw);
}

export async function updateListing(
  id: string,
  body: Omit<CreateListingInput, "vehicleId">,
): Promise<HostListingViewModel> {
  const raw = await api.patch<RawListingDetail>(`/host/listings/${id}`, body);
  return mapListingDetail(raw);
}

export interface CreateExtraInput {
  name: string;
  pricingType: "PER_DAY" | "PER_TRIP";
  price: number;
}

export interface UpdateExtraInput {
  name?: string;
  pricingType?: "PER_DAY" | "PER_TRIP";
  price?: number;
  active?: boolean;
}

export async function getHostListingExtras(id: string): Promise<HostListingExtraViewModel[]> {
  const raw = await api.get<RawExtra[]>(`/host/listings/${id}/extras`);
  return raw.map(mapExtra);
}

export async function createHostListingExtra(
  listingId: string,
  body: CreateExtraInput,
): Promise<HostListingExtraViewModel> {
  const raw = await api.post<RawExtra>(`/host/listings/${listingId}/extras`, body);
  return mapExtra(raw);
}

export async function updateHostListingExtra(
  listingId: string,
  extraId: string,
  body: UpdateExtraInput,
): Promise<HostListingExtraViewModel> {
  const raw = await api.patch<RawExtra>(`/host/listings/${listingId}/extras/${extraId}`, body);
  return mapExtra(raw);
}

export async function deleteHostListingExtra(listingId: string, extraId: string): Promise<void> {
  await api.delete(`/host/listings/${listingId}/extras/${extraId}`);
}

export interface CreateListingInput {
  vehicleId: string;
  title: string;
  description: string;
  city: string;
  address: string;
  basePricePerDay: number;
  dailyKmLimit: number;
  instantBook: boolean;
  cancellationPolicy: "FLEXIBLE" | "MODERATE" | "STRICT";
}

export async function createListing(
  body: CreateListingInput,
): Promise<HostListingViewModel> {
  const raw = await api.post<RawListingDetail>("/host/listings", body);
  return mapListingDetail(raw);
}

export class ListingTransitionError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly apiError?: ApiError,
  ) {
    super(message);
    this.name = "ListingTransitionError";
  }
}

function handleListingError(err: unknown): never {
  if (err instanceof ApiError) {
    throw new ListingTransitionError(err.code, err.message, err);
  }
  if (err instanceof Error) {
    throw new ListingTransitionError("UNKNOWN", err.message);
  }
  throw new ListingTransitionError("UNKNOWN", "An unexpected error occurred");
}

export async function submitListingSafe(
  id: string,
): Promise<HostListingViewModel> {
  try {
    return await submitListing(id);
  } catch (err) {
    handleListingError(err);
  }
}

export async function archiveListingSafe(
  id: string,
): Promise<HostListingViewModel> {
  try {
    return await archiveListing(id);
  } catch (err) {
    handleListingError(err);
  }
}

export async function reactivateListingSafe(
  id: string,
): Promise<HostListingViewModel> {
  try {
    return await reactivateListing(id);
  } catch (err) {
    handleListingError(err);
  }
}

export async function resumeListingSafe(
  id: string,
): Promise<HostListingViewModel> {
  try {
    return await resumeListing(id);
  } catch (err) {
    handleListingError(err);
  }
}
