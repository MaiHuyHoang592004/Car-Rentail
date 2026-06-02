import type { HostVehicleStatus, HostVehicleViewModel } from "@/features/host/types";

export type HostVehicleFilterValue = "ALL" | HostVehicleStatus;

export const HOST_VEHICLE_STATUS_FILTERS: HostVehicleFilterValue[] = [
  "ALL",
  "DRAFT",
  "ACTIVE",
  "MAINTENANCE",
  "SUSPENDED",
  "ARCHIVED",
];

const READABLE_IDENTIFIER_INTEGRITY = {
  plateNumberReadable: true,
  vinReadable: true,
  hasUnreadableEncryptedFields: false,
};

export const HOST_VEHICLES: HostVehicleViewModel[] = [
  {
    id: "veh-001",
    category: "SEDAN",
    make: "Toyota",
    model: "Vios",
    year: 2022,
    transmission: "AUTO",
    fuelType: "GASOLINE",
    seats: 5,
    status: "ACTIVE",
    city: "Ho Chi Minh City",
    plateNumber: "51H-112.45",
    vin: "VF1TR87654A120001",
    identifierIntegrity: READABLE_IDENTIFIER_INTEGRITY,
  },
  {
    id: "veh-002",
    category: "SUV",
    make: "Hyundai",
    model: "Santa Fe",
    year: 2023,
    transmission: "AUTO",
    fuelType: "DIESEL",
    seats: 7,
    status: "MAINTENANCE",
    city: "Da Nang",
    plateNumber: "43A-208.31",
    vin: "HF2DA76833K991274",
    identifierIntegrity: READABLE_IDENTIFIER_INTEGRITY,
  },
  {
    id: "veh-003",
    category: "SUV",
    make: "VinFast",
    model: "VF8",
    year: 2024,
    transmission: "AUTO",
    fuelType: "EV",
    seats: 5,
    status: "DRAFT",
    city: "Hanoi",
    plateNumber: "30L-086.52",
    vin: "VF8VN28374J110992",
    identifierIntegrity: READABLE_IDENTIFIER_INTEGRITY,
  },
  {
    id: "veh-004",
    category: "HATCHBACK",
    make: "Kia",
    model: "Morning",
    year: 2021,
    transmission: "MANUAL",
    fuelType: "GASOLINE",
    seats: 4,
    status: "SUSPENDED",
    city: "Can Tho",
    plateNumber: "65A-089.77",
    vin: "KIMRN55673P220114",
    identifierIntegrity: READABLE_IDENTIFIER_INTEGRITY,
  },
  {
    id: "veh-005",
    category: "PICKUP",
    make: "Ford",
    model: "Ranger",
    year: 2020,
    transmission: "AUTO",
    fuelType: "DIESEL",
    seats: 5,
    status: "ARCHIVED",
    city: "Ho Chi Minh City",
    plateNumber: "51D-550.19",
    vin: "FRDRA44509N994513",
    identifierIntegrity: READABLE_IDENTIFIER_INTEGRITY,
  },
];

export function getHostVehiclesByStatus(status: HostVehicleFilterValue): HostVehicleViewModel[] {
  if (status === "ALL") {
    return [...HOST_VEHICLES];
  }
  return HOST_VEHICLES.filter((vehicle) => vehicle.status === status);
}

export function getHostVehicleById(id: string): HostVehicleViewModel | null {
  const vehicle = HOST_VEHICLES.find((item) => item.id === id);
  if (!vehicle) {
    return null;
  }
  return { ...vehicle };
}

export function getHostActiveVehicles(): HostVehicleViewModel[] {
  return HOST_VEHICLES.filter((vehicle) => vehicle.status === "ACTIVE").map((vehicle) => ({
    ...vehicle,
  }));
}
