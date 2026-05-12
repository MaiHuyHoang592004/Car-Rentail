import type { VehicleFormErrors, VehicleFormState } from "@/features/host/forms";
import type { HostVehicleViewModel } from "@/features/host/types";

export function validateVehicleForm(form: VehicleFormState): VehicleFormErrors {
  const errors: VehicleFormErrors = {};
  const currentYear = new Date().getFullYear();

  if (!form.category.trim()) {
    errors.category = "Category is required.";
  }
  if (!form.make.trim()) {
    errors.make = "Make is required.";
  }
  if (!form.model.trim()) {
    errors.model = "Model is required.";
  }
  if (!form.city.trim()) {
    errors.city = "City is required.";
  }
  if (!form.plateNumber.trim()) {
    errors.plateNumber = "Plate number is required.";
  }

  const year = Number(form.year);
  if (!form.year.trim() || Number.isNaN(year)) {
    errors.year = "Year is required.";
  } else if (year < 1995 || year > currentYear + 1) {
    errors.year = `Year must be between 1995 and ${currentYear + 1}.`;
  }

  const seats = Number(form.seats);
  if (!form.seats.trim() || Number.isNaN(seats)) {
    errors.seats = "Seats is required.";
  } else if (seats <= 0) {
    errors.seats = "Seats must be greater than zero.";
  }

  return errors;
}

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
    plateNumber: vehicle.plateNumber,
    vin: vehicle.vin,
  };
}
