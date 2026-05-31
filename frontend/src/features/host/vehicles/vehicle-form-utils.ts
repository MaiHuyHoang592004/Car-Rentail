import type { VehicleFormState } from "@/features/host/forms";
import type { HostVehicleViewModel } from "@/features/host/types";

export function buildVehicleFormFromViewModel(vehicle: HostVehicleViewModel): VehicleFormState {
  return {
    category: vehicle.category,
    make: vehicle.make,
    model: vehicle.model,
    year: String(vehicle.year),
    transmission: vehicle.transmission,
    fuelType: vehicle.fuelType,
    seats: String(vehicle.seats),
    status: vehicle.status,
    city: vehicle.city,
    plateNumber: vehicle.plateNumber ?? "",
    vin: vehicle.vin ?? "",
  };
}
