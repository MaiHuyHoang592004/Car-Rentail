import type { HostAvailabilityDayViewModel } from "@/features/host/types";

type AvailabilityStore = Record<string, HostAvailabilityDayViewModel[]>;

const HOST_AVAILABILITY: AvailabilityStore = {
  "hst-lst-001": [
    { date: "2026-06-01", status: "FREE" },
    { date: "2026-06-02", status: "FREE" },
    { date: "2026-06-03", status: "HOLD", bookingId: "bk-1001", expiresAt: "2026-05-12T21:45:00+07:00" },
    { date: "2026-06-04", status: "BOOKED", bookingId: "bk-0982" },
    { date: "2026-06-05", status: "BLOCKED" },
    { date: "2026-06-06", status: "FREE" },
    { date: "2026-06-07", status: "FREE" },
    { date: "2026-06-08", status: "BOOKED", bookingId: "bk-0901" },
    { date: "2026-06-09", status: "FREE" },
    { date: "2026-06-10", status: "BLOCKED" },
  ],
  "hst-lst-002": [
    { date: "2026-06-01", status: "FREE" },
    { date: "2026-06-02", status: "FREE" },
    { date: "2026-06-03", status: "FREE" },
    { date: "2026-06-04", status: "BLOCKED" },
    { date: "2026-06-05", status: "BLOCKED" },
    { date: "2026-06-06", status: "FREE" },
    { date: "2026-06-07", status: "HOLD", bookingId: "bk-1004", expiresAt: "2026-05-13T09:10:00+07:00" },
    { date: "2026-06-08", status: "FREE" },
    { date: "2026-06-09", status: "BOOKED", bookingId: "bk-0877" },
    { date: "2026-06-10", status: "FREE" },
  ],
  "hst-lst-003": [
    { date: "2026-06-01", status: "FREE" },
    { date: "2026-06-02", status: "FREE" },
    { date: "2026-06-03", status: "FREE" },
    { date: "2026-06-04", status: "FREE" },
    { date: "2026-06-05", status: "FREE" },
    { date: "2026-06-06", status: "FREE" },
    { date: "2026-06-07", status: "FREE" },
    { date: "2026-06-08", status: "FREE" },
    { date: "2026-06-09", status: "FREE" },
    { date: "2026-06-10", status: "FREE" },
  ],
  "hst-lst-004": [
    { date: "2026-06-01", status: "BLOCKED" },
    { date: "2026-06-02", status: "BLOCKED" },
    { date: "2026-06-03", status: "FREE" },
    { date: "2026-06-04", status: "FREE" },
    { date: "2026-06-05", status: "BOOKED", bookingId: "bk-0821" },
    { date: "2026-06-06", status: "BOOKED", bookingId: "bk-0821" },
    { date: "2026-06-07", status: "FREE" },
    { date: "2026-06-08", status: "FREE" },
    { date: "2026-06-09", status: "HOLD", bookingId: "bk-1040", expiresAt: "2026-05-13T11:20:00+07:00" },
    { date: "2026-06-10", status: "FREE" },
  ],
};

type AvailabilityMutationResult = {
  nextDays: HostAvailabilityDayViewModel[];
  updatedCount: number;
  skippedDates: string[];
};

export function getHostAvailabilityByListingId(listingId: string): HostAvailabilityDayViewModel[] {
  const days = HOST_AVAILABILITY[listingId] ?? HOST_AVAILABILITY["hst-lst-001"];
  return days.map((day) => ({ ...day }));
}

export function blockAvailabilityDates(
  currentDays: HostAvailabilityDayViewModel[],
  selectedDates: string[],
): AvailabilityMutationResult {
  const selectedSet = new Set(selectedDates);
  let updatedCount = 0;
  const skippedDates: string[] = [];

  const nextDays: HostAvailabilityDayViewModel[] = currentDays.map((day) => {
    if (!selectedSet.has(day.date)) {
      return day;
    }
    if (day.status !== "FREE") {
      skippedDates.push(day.date);
      return day;
    }
    updatedCount += 1;
    return { ...day, status: "BLOCKED" as const, bookingId: undefined, expiresAt: undefined };
  });

  return { nextDays, updatedCount, skippedDates };
}

export function unblockAvailabilityDates(
  currentDays: HostAvailabilityDayViewModel[],
  selectedDates: string[],
): AvailabilityMutationResult {
  const selectedSet = new Set(selectedDates);
  let updatedCount = 0;
  const skippedDates: string[] = [];

  const nextDays: HostAvailabilityDayViewModel[] = currentDays.map((day) => {
    if (!selectedSet.has(day.date)) {
      return day;
    }
    if (day.status !== "BLOCKED") {
      skippedDates.push(day.date);
      return day;
    }
    updatedCount += 1;
    return { ...day, status: "FREE" as const };
  });

  return { nextDays, updatedCount, skippedDates };
}

export function countBlockedDatesAcrossHost(): number {
  return Object.values(HOST_AVAILABILITY)
    .flat()
    .filter((day) => day.status === "BLOCKED").length;
}
