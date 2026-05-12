import type { HostListingStatus, HostListingViewModel } from "@/features/host/types";

export type HostListingFilterValue = "ALL" | HostListingStatus;

export const HOST_LISTING_STATUS_FILTERS: HostListingFilterValue[] = [
  "ALL",
  "DRAFT",
  "PENDING_APPROVAL",
  "ACTIVE",
  "SUSPENDED",
  "ARCHIVED",
];

export const HOST_LISTINGS: HostListingViewModel[] = [
  {
    id: "hst-lst-001",
    vehicleId: "veh-001",
    vehicleLabel: "Toyota Vios 2022",
    title: "Toyota Vios City Saver",
    description: "Reliable sedan for city rides and airport pickups.",
    city: "Ho Chi Minh City",
    address: "District 7, Ho Chi Minh City",
    basePricePerDay: 700000,
    currency: "VND",
    dailyKmLimit: 200,
    instantBook: false,
    cancellationPolicy: "FLEXIBLE",
    status: "ACTIVE",
  },
  {
    id: "hst-lst-002",
    vehicleId: "veh-002",
    vehicleLabel: "Hyundai Santa Fe 2023",
    title: "Santa Fe Family Trip",
    description: "7-seat SUV suitable for long-distance family travel.",
    city: "Da Nang",
    address: "Hai Chau District, Da Nang",
    basePricePerDay: 1250000,
    currency: "VND",
    dailyKmLimit: 250,
    instantBook: true,
    cancellationPolicy: "MODERATE",
    status: "PENDING_APPROVAL",
  },
  {
    id: "hst-lst-003",
    vehicleId: "veh-003",
    vehicleLabel: "VinFast VF8 2024",
    title: "VF8 Premium EV",
    description: "Electric SUV with modern safety package.",
    city: "Hanoi",
    address: "Cau Giay District, Hanoi",
    basePricePerDay: 1500000,
    currency: "VND",
    dailyKmLimit: 220,
    instantBook: false,
    cancellationPolicy: "STRICT",
    status: "DRAFT",
  },
  {
    id: "hst-lst-004",
    vehicleId: "veh-004",
    vehicleLabel: "Kia Morning 2021",
    title: "Kia Morning Budget",
    description: "Compact budget option for short city routes.",
    city: "Can Tho",
    address: "Ninh Kieu District, Can Tho",
    basePricePerDay: 480000,
    currency: "VND",
    dailyKmLimit: 150,
    instantBook: false,
    cancellationPolicy: "FLEXIBLE",
    status: "SUSPENDED",
  },
  {
    id: "hst-lst-005",
    vehicleId: "veh-005",
    vehicleLabel: "Ford Ranger 2020",
    title: "Ford Ranger Utility",
    description: "Pickup truck listing retained for record only.",
    city: "Ho Chi Minh City",
    address: "Thu Duc, Ho Chi Minh City",
    basePricePerDay: 980000,
    currency: "VND",
    dailyKmLimit: 180,
    instantBook: false,
    cancellationPolicy: "MODERATE",
    status: "ARCHIVED",
  },
];

export function getHostListingsByStatus(status: HostListingFilterValue): HostListingViewModel[] {
  if (status === "ALL") {
    return [...HOST_LISTINGS];
  }
  return HOST_LISTINGS.filter((listing) => listing.status === status);
}

export function getHostListingById(id: string): HostListingViewModel | null {
  const listing = HOST_LISTINGS.find((item) => item.id === id);
  if (!listing) {
    return null;
  }
  return { ...listing };
}

export function submitListingTransition(listing: HostListingViewModel): HostListingViewModel | null {
  if (listing.status !== "DRAFT") {
    return null;
  }
  return { ...listing, status: "PENDING_APPROVAL" };
}

export function archiveListingTransition(listing: HostListingViewModel): HostListingViewModel | null {
  if (!["DRAFT", "PENDING_APPROVAL", "ACTIVE"].includes(listing.status)) {
    return null;
  }
  return { ...listing, status: "ARCHIVED" };
}

export function reactivateListingTransition(listing: HostListingViewModel): HostListingViewModel | null {
  if (listing.status !== "SUSPENDED") {
    return null;
  }
  return { ...listing, status: "ACTIVE" };
}
