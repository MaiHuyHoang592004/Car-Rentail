import type { HostVehicleStatus } from "@/features/host/types";

export type VehicleFormState = {
  category: string;
  make: string;
  model: string;
  year: string;
  transmission: "AUTO" | "MANUAL";
  fuelType: string;
  seats: string;
  status: HostVehicleStatus;
  city: string;
  plateNumber: string;
  vin: string;
};

export type VehicleFormErrors = Partial<Record<keyof VehicleFormState | "form", string>>;

export type HostListingFormState = {
  vehicleId: string;
  title: string;
  description: string;
  city: string;
  address: string;
  basePricePerDay: string;
  dailyKmLimit: string;
  instantBook: boolean;
  cancellationPolicy: "FLEXIBLE" | "MODERATE" | "STRICT";
};

export type HostListingFormErrors = Partial<Record<keyof HostListingFormState | "form", string>>;

export type AvailabilitySelectionState = {
  selectedDates: string[];
};
