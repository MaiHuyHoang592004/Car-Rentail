import type { HostVehicleStatus } from "@/features/host/types";

export type HostVehicleFilterValue = "ALL" | HostVehicleStatus;

export interface HostVehicleSelectOption {
  id: string;
  label: string;
}

export interface VehicleResponse {
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
  plateNumber: string;
  vin: string;
}

export interface VehiclePageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface UpdateVehicleInput {
  category?: string;
  make?: string;
  model?: string;
  year?: number;
  transmission?: string;
  fuelType?: string;
  seats?: number;
  status?: string;
  city?: string;
}

export interface CreateVehicleInput {
  category: string;
  make: string;
  model: string;
  year: number;
  plateNumber: string;
  vin?: string;
  transmission: string;
  fuelType: string;
  seats: number;
  city: string;
  status?: string;
}
