import { api } from "@/lib/api-client";
import type { HostAvailabilityStatus, HostAvailabilityDayViewModel } from "@/features/host/types";

interface HostDateStatus {
  date: string; // ISO date string "YYYY-MM-DD"
  status: HostAvailabilityStatus;
  bookingId: string[] | null;
  expiresAt: string | null;
}

interface HostAvailabilityResponse {
  listingId: string;
  from: string;
  to: string;
  dates: HostDateStatus[];
}

interface AvailabilityUpdateResponse {
  updatedCount: number;
}

function mapHostDateStatus(raw: HostDateStatus): HostAvailabilityDayViewModel {
  return {
    date: raw.date,
    status: raw.status,
    bookingId: raw.bookingId?.[0] ?? undefined,
    expiresAt: raw.expiresAt ?? undefined,
  };
}

export async function getHostAvailabilityByListingId(
  listingId: string,
  from: string,
  to: string,
): Promise<HostAvailabilityDayViewModel[]> {
  const data = await api.get<HostAvailabilityResponse>(
    `/host/listings/${listingId}/availability?from=${from}&to=${to}`,
  );
  return data.dates.map(mapHostDateStatus);
}

export async function blockAvailabilityDates(
  listingId: string,
  dates: string[],
): Promise<number> {
  const parsed = dates.map((d) => d.replace(/T.+$/, ""));
  const data = await api.post<AvailabilityUpdateResponse>(
    `/host/listings/${listingId}/availability/block`,
    { dates: parsed },
  );
  return data.updatedCount;
}

export async function unblockAvailabilityDates(
  listingId: string,
  dates: string[],
): Promise<number> {
  const parsed = dates.map((d) => d.replace(/T.+$/, ""));
  const data = await api.post<AvailabilityUpdateResponse>(
    `/host/listings/${listingId}/availability/unblock`,
    { dates: parsed },
  );
  return data.updatedCount;
}
