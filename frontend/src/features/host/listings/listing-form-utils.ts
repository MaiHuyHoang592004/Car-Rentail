import type { HostListingFormState } from "@/features/host/forms";
import type { HostListingViewModel } from "@/features/host/types";

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
