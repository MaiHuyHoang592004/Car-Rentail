export type HostVehicleStatus = "DRAFT" | "ACTIVE" | "MAINTENANCE" | "SUSPENDED" | "ARCHIVED";

export type HostListingStatus = "DRAFT" | "PENDING_APPROVAL" | "ACTIVE" | "SUSPENDED" | "ARCHIVED";

export type HostAvailabilityStatus = "FREE" | "HOLD" | "BOOKED" | "BLOCKED";

export type HostVehicleViewModel = {
  id: string;
  category: string;
  make: string;
  model: string;
  year: number;
  transmission: "AUTO" | "MANUAL";
  fuelType: string;
  seats: number;
  status: HostVehicleStatus;
  city: string;
  plateNumber: string | null;
  vin: string | null;
  identifierIntegrity: {
    plateNumberReadable: boolean;
    vinReadable: boolean;
    hasUnreadableEncryptedFields: boolean;
  };
  primaryPhotoUrl?: string | null;
  photos?: {
    id: string;
    fileId: string;
    primary: boolean;
    displayOrder: number;
    signedUrl: string;
  }[];
};

export type HostListingSummaryViewModel = {
  id: string;
  vehicleId: string;
  vehicleLabel: string;
  title: string;
  city: string;
  basePricePerDay: number;
  currency: string;
  status: HostListingStatus;
};

export type HostListingDetailViewModel = HostListingSummaryViewModel & {
  description: string;
  address: string;
  dailyKmLimit: number;
  instantBook: boolean;
  cancellationPolicy: "FLEXIBLE" | "MODERATE" | "STRICT";
  suspensionReason?: string | null;
  suspensionSource?: string | null;
  suspensionUntil?: string | null;
  extras: HostListingExtraViewModel[];
};

export type HostListingViewModel = HostListingDetailViewModel;

export type HostListingExtraViewModel = {
  id: string;
  name: string;
  pricingType: "PER_DAY" | "PER_TRIP";
  price: number;
  active: boolean;
};

export type HostAvailabilityDayViewModel = {
  date: string;
  status: HostAvailabilityStatus;
  bookingId?: string;
  expiresAt?: string;
};

export type HostDashboardMetricsViewModel = {
  totalVehicles: number;
  activeListings: number;
  pendingApprovals: number;
  blockedDates: number;
};
