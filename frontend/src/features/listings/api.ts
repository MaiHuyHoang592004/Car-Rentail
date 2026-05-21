import { api } from "@/lib/api-client";
import type { ListingCardViewModel, ListingFilterState } from "@/features/listings/types";

const FALLBACK_COVER_IMAGE_URL =
  "https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?auto=format&fit=crop&w=1200&q=80";

export const DEFAULT_LISTING_FILTERS: ListingFilterState = {
  city: "",
  pickupDate: "",
  returnDate: "",
  category: "ALL",
  transmission: "ALL",
  fuelType: "ALL",
  seats: "",
  minPrice: "",
  maxPrice: "",
};

type RawListingSearchResponse = {
  id: string;
  title: string;
  city: string;
  category: string | null;
  basePricePerDay: number | string;
  currency: string;
  seats: number | null;
  transmission: "AUTO" | "MANUAL" | null;
  fuelType: string | null;
  coverPhotoUrl: string | null;
  ratingAverage: number | string | null;
};

type RawPageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ListingSearchPage = {
  content: ListingCardViewModel[];
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

function addIfPresent(params: URLSearchParams, key: string, value: string) {
  const trimmed = value.trim();
  if (trimmed) {
    params.set(key, trimmed);
  }
}

export function buildListingSearchQuery(
  filters: ListingFilterState,
  page = 0,
  size = 20,
): string {
  const params = new URLSearchParams();
  addIfPresent(params, "city", filters.city);
  addIfPresent(params, "pickupDate", filters.pickupDate);
  addIfPresent(params, "returnDate", filters.returnDate);
  addIfPresent(params, "minPrice", filters.minPrice);
  addIfPresent(params, "maxPrice", filters.maxPrice);
  addIfPresent(params, "seats", filters.seats);
  if (filters.category !== "ALL") params.set("categories", filters.category);
  if (filters.transmission !== "ALL") params.set("transmission", filters.transmission);
  if (filters.fuelType !== "ALL") params.set("fuelType", filters.fuelType);
  params.set("page", String(page));
  params.set("size", String(size));
  return params.toString();
}

export function mapListingSearchResponse(raw: RawListingSearchResponse): ListingCardViewModel {
  const rating = raw.ratingAverage == null ? null : toNumber(raw.ratingAverage);
  return {
    id: raw.id,
    title: raw.title,
    city: raw.city,
    category: raw.category ?? "UNKNOWN",
    basePricePerDay: toNumber(raw.basePricePerDay),
    currency: "VND",
    seats: raw.seats ?? 0,
    transmission: raw.transmission ?? "AUTO",
    fuelType: raw.fuelType ?? "UNKNOWN",
    status: "ACTIVE",
    coverImageUrl: raw.coverPhotoUrl || FALLBACK_COVER_IMAGE_URL,
    ratingLabel: rating == null || rating <= 0 ? "New listing" : `${rating.toFixed(1)} rating`,
  };
}

export async function searchListings(
  filters: ListingFilterState,
  page = 0,
  size = 20,
): Promise<ListingSearchPage> {
  const query = buildListingSearchQuery(filters, page, size);
  const raw = await api.get<RawPageResponse<RawListingSearchResponse>>(`/listings?${query}`, {
    skipAuth: true,
  });
  return {
    content: raw.content.map(mapListingSearchResponse),
    page: raw.page,
    size: raw.size,
    totalElements: raw.totalElements,
    totalPages: raw.totalPages,
  };
}
