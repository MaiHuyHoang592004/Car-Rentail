export type AdminListingStatus =
  | "DRAFT"
  | "PENDING_APPROVAL"
  | "ACTIVE"
  | "SUSPENDED"
  | "ARCHIVED";

export type AdminListingFilterValue = "ALL" | AdminListingStatus;

export const ADMIN_LISTING_STATUS_FILTERS: AdminListingFilterValue[] = [
  "ALL",
  "PENDING_APPROVAL",
  "ACTIVE",
  "SUSPENDED",
  "DRAFT",
  "ARCHIVED",
];

export type AdminListingSummary = {
  id: string;
  title: string;
  city: string;
  status: AdminListingStatus;
  basePricePerDay: number;
  currency: string;
  createdAt: string;
};

export type AdminListingDetail = {
  listing: {
    id: string;
    vehicleId: string;
    title: string;
    description: string;
    city: string;
    address: string;
    basePricePerDay: number;
    currency: string;
    dailyKmLimit: number;
    instantBook: boolean;
    cancellationPolicy: "FLEXIBLE" | "MODERATE" | "STRICT";
    status: AdminListingStatus;
    createdAt: string;
  };
  host: {
    id: string;
    fullName: string;
    email: string;
  } | null;
  bookingSummary: {
    activeBookings: number;
  };
};

export type AdminListingPage = {
  listings: AdminListingSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};