import { api } from "@/lib/api-client";

export type HostOverviewReportViewModel = {
  from: string;
  to: string;
  grossCaptured: number;
  netEarnings: number;
  bookingCount: number;
  activeListings: number;
  pendingApprovalListings: number;
  blockedDays: number;
  holdDays: number;
  bookedDays: number;
  generatedDays: number;
  occupancyRate: number;
  blockedRate: number;
};

type RawOverviewReport = {
  from: string;
  to: string;
  grossCaptured: number | string;
  netEarnings: number | string;
  bookingCount: number;
  activeListings: number;
  pendingApprovalListings: number;
  blockedDays: number;
  holdDays: number;
  bookedDays: number;
  generatedDays: number;
  occupancyRate: number | string;
  blockedRate: number | string;
};

function toNumber(value: number | string | null | undefined): number {
  if (value === null || value === undefined) return 0;
  if (typeof value === "number") return value;
  const parsed = Number.parseFloat(String(value));
  return Number.isFinite(parsed) ? parsed : 0;
}

export async function getHostOverviewReport(from: string, to: string): Promise<HostOverviewReportViewModel> {
  const raw = await api.get<RawOverviewReport>(`/host/reports/overview?from=${from}&to=${to}`);
  return {
    from: raw.from,
    to: raw.to,
    grossCaptured: toNumber(raw.grossCaptured),
    netEarnings: toNumber(raw.netEarnings),
    bookingCount: raw.bookingCount,
    activeListings: raw.activeListings,
    pendingApprovalListings: raw.pendingApprovalListings,
    blockedDays: raw.blockedDays,
    holdDays: raw.holdDays,
    bookedDays: raw.bookedDays,
    generatedDays: raw.generatedDays,
    occupancyRate: toNumber(raw.occupancyRate),
    blockedRate: toNumber(raw.blockedRate),
  };
}
