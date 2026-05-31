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

export type HostListingViewModel = {
  id: string;
  vehicleId: string;
  vehicleLabel: string;
  title: string;
  description: string;
  city: string;
  address: string;
  basePricePerDay: number;
  currency: "VND";
  dailyKmLimit: number;
  instantBook: boolean;
  cancellationPolicy: "FLEXIBLE" | "MODERATE" | "STRICT";
  status: HostListingStatus;
  suspensionReason?: string | null;
  suspensionSource?: string | null;
  suspensionUntil?: string | null;
  extras: HostListingExtraViewModel[];
};

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
