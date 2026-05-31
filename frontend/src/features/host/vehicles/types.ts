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
  plateNumber: string | null;
  vin: string | null;
  identifierIntegrity: {
    plateNumberReadable: boolean;
    vinReadable: boolean;
    hasUnreadableEncryptedFields: boolean;
  };
  primaryPhotoUrl?: string | null;
  photos?: VehiclePhotoResponse[];
}

export interface VehiclePhotoResponse {
  id: string;
  vehicleId: string;
  fileId: string;
  primary: boolean;
  displayOrder: number;
  visibility: string;
  signedUrl: string;
  signedUrlExpiresAt: string;
}

export interface AddVehiclePhotoInput {
  fileId: string;
  primary?: boolean;
  displayOrder?: number;
}

export interface CreateVehiclePhotoUploadIntentInput {
  contentType: string;
  sizeBytes: number;
  checksum?: string;
}

export interface FileUploadIntentResponse {
  fileId: string;
  bucket: string;
  objectKey: string;
  uploadUrl: string;
  expiresAt: string;
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
