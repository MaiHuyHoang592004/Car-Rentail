import { api } from "@/lib/api-client";
import type { HostVehicleViewModel } from "@/features/host/types";
import type {
  CreateVehicleInput,
  HostVehicleFilterValue,
  HostVehicleSelectOption,
  UpdateVehicleInput,
  VehiclePageResponse,
  VehicleResponse,
} from "@/features/host/vehicles/types";

export type {
  CreateVehicleInput,
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

export async function createHostVehicle(
  body: CreateVehicleInput,
): Promise<string> {
  const result = await api.post<VehicleResponse>("/host/vehicles", body);
  return result.id;
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
