import type { HostListingFormErrors, HostListingFormState } from "@/features/host/forms";
import type { HostListingViewModel } from "@/features/host/types";

export function validateListingForm(form: HostListingFormState): HostListingFormErrors {
  const errors: HostListingFormErrors = {};

  if (!form.vehicleId) {
    errors.vehicleId = "Vehicle is required.";
  }
  if (!form.title.trim()) {
    errors.title = "Title is required.";
  }
  if (!form.description.trim()) {
    errors.description = "Description is required.";
  }
  if (!form.city.trim()) {
    errors.city = "City is required.";
  }
  if (!form.address.trim()) {
    errors.address = "Address is required.";
  }

  const basePrice = Number(form.basePricePerDay);
  if (!form.basePricePerDay.trim() || Number.isNaN(basePrice)) {
    errors.basePricePerDay = "Base price per day is required.";
  } else if (basePrice <= 0) {
    errors.basePricePerDay = "Base price must be greater than zero.";
  }

  const dailyKmLimit = Number(form.dailyKmLimit);
  if (!form.dailyKmLimit.trim() || Number.isNaN(dailyKmLimit)) {
    errors.dailyKmLimit = "Daily km limit is required.";
  } else if (dailyKmLimit <= 0) {
    errors.dailyKmLimit = "Daily km limit must be greater than zero.";
  }

  return errors;
}

export function buildListingFormFromViewModel(listing: HostListingViewModel): HostListingFormState {
  return {
    vehicleId: listing.vehicleId,
    title: listing.title,
    description: listing.description,
    city: listing.city,
    address: listing.address,
    basePricePerDay: String(listing.basePricePerDay),
    dailyKmLimit: String(listing.dailyKmLimit),
    instantBook: listing.instantBook,
    cancellationPolicy: listing.cancellationPolicy,
  };
}
