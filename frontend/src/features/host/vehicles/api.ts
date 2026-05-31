import { api } from "@/lib/api-client";
import type { HostVehicleViewModel } from "@/features/host/types";
import type {
  CreateVehicleInput,
  AddVehiclePhotoInput,
  CreateVehiclePhotoUploadIntentInput,
  FileUploadIntentResponse,
  HostVehicleFilterValue,
  HostVehicleSelectOption,
  UpdateVehicleInput,
  VehiclePageResponse,
  VehiclePhotoResponse,
  VehicleResponse,
} from "@/features/host/vehicles/types";

export type {
  CreateVehicleInput,
  AddVehiclePhotoInput,
  CreateVehiclePhotoUploadIntentInput,
  HostVehicleFilterValue,
  HostVehicleSelectOption,
  UpdateVehicleInput,
} from "@/features/host/vehicles/types";

export const HOST_VEHICLE_STATUS_FILTERS: HostVehicleFilterValue[] = [
  "ALL",
  "DRAFT",
  "ACTIVE",
  "MAINTENANCE",
  "SUSPENDED",
  "ARCHIVED",
];

function mapVehicleResponseToViewModel(r: VehicleResponse): HostVehicleViewModel {
  return {
    id: r.id,
    category: r.category,
    make: r.make,
    model: r.model,
    year: r.year,
    transmission: r.transmission,
    fuelType: r.fuelType,
    seats: r.seats,
    status: r.status,
    city: r.city,
    plateNumber: r.plateNumber,
    vin: r.vin,
    identifierIntegrity: r.identifierIntegrity,
    primaryPhotoUrl: r.primaryPhotoUrl,
    photos: r.photos?.map((photo) => ({
      id: photo.id,
      fileId: photo.fileId,
      primary: photo.primary,
      displayOrder: photo.displayOrder,
      signedUrl: photo.signedUrl,
    })),
  };
}

function isNotFoundError(error: unknown): boolean {
  if (error instanceof Error && "response" in error) {
    const resp = (error as unknown as { response?: { status?: number } }).response;
    return resp?.status === 404;
  }
  return false;
}

export async function getHostVehiclesByStatus(
  status?: HostVehicleFilterValue,
): Promise<HostVehicleViewModel[]> {
  const params = new URLSearchParams();
  if (status && status !== "ALL") params.set("status", status);
  const data = await api.get<VehiclePageResponse<VehicleResponse>>(
    `/host/vehicles?${params}`,
  );
  return data.content.map(mapVehicleResponseToViewModel);
}

export async function getHostVehicleById(
  id: string,
): Promise<HostVehicleViewModel | null> {
  try {
    return mapVehicleResponseToViewModel(
      await api.get<VehicleResponse>(`/host/vehicles/${id}`),
    );
  } catch (error) {
    if (isNotFoundError(error)) return null;
    throw error;
  }
}

export async function updateHostVehicle(
  id: string,
  body: UpdateVehicleInput,
): Promise<void> {
  await api.patch(`/host/vehicles/${id}`, body);
}

export async function archiveHostVehicle(id: string): Promise<void> {
  await api.delete(`/host/vehicles/${id}`);
}

type RawArchivePreview = {
  vehicleId: string;
  affectedListings: { id: string; title: string; status: string }[];
  hasActiveBookings: boolean;
  archiveAllowed: boolean;
  blockingReason: string | null;
};

export type HostVehicleArchivePreview = {
  vehicleId: string;
  affectedListings: { id: string; title: string; status: string }[];
  hasActiveBookings: boolean;
  archiveAllowed: boolean;
  blockingReason?: string;
};

export async function getHostVehicleArchivePreview(id: string): Promise<HostVehicleArchivePreview> {
  const raw = await api.get<RawArchivePreview>(`/host/vehicles/${id}/archive-preview`);
  return {
    vehicleId: raw.vehicleId,
    affectedListings: raw.affectedListings,
    hasActiveBookings: raw.hasActiveBookings,
    archiveAllowed: raw.archiveAllowed,
    blockingReason: raw.blockingReason ?? undefined,
  };
}

export async function createHostVehicle(
  body: CreateVehicleInput,
): Promise<string> {
  const result = await api.post<VehicleResponse>("/host/vehicles", body);
  return result.id;
}

export async function addHostVehiclePhoto(
  vehicleId: string,
  body: AddVehiclePhotoInput,
): Promise<VehiclePhotoResponse> {
  return api.post<VehiclePhotoResponse>(`/host/vehicles/${vehicleId}/photos`, body);
}

export async function createHostVehiclePhotoUploadIntent(
  vehicleId: string,
  body: CreateVehiclePhotoUploadIntentInput,
): Promise<FileUploadIntentResponse> {
  return api.post<FileUploadIntentResponse>(`/host/vehicles/${vehicleId}/photos/upload-intents`, body);
}

export async function finalizeFileUpload(fileId: string): Promise<void> {
  await api.post(`/files/${fileId}/finalize`, {});
}

export async function getHostActiveVehicles(): Promise<HostVehicleSelectOption[]> {
  const data = await api.get<VehiclePageResponse<VehicleResponse>>(
    "/host/vehicles?status=ACTIVE&size=100",
  );
  return data.content.map((v) => ({
    id: v.id,
    label: `${v.make} ${v.model} (${v.year})`,
  }));
}
