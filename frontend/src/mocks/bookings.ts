import type {
  BookingDetailViewModel,
  BookingListFilterValue,
  BookingSummaryViewModel,
} from "@/features/bookings/types";

export const BOOKING_STATUS_FILTERS: BookingListFilterValue[] = [
  "ALL",
  "HELD",
  "PENDING_HOST_APPROVAL",
  "CONFIRMED",
  "IN_PROGRESS",
  "COMPLETED",
  "CANCELLED",
  "REJECTED",
  "EXPIRED",
];

export const BOOKING_SUMMARIES: BookingSummaryViewModel[] = [
  {
    id: "bk-1001",
    status: "HELD",
    listingId: "lst-001",
    listingTitle: "Toyota Vios 2022",
    createdAt: "2026-05-12T10:00:00+07:00",
    pickupDate: "2026-06-18",
    returnDate: "2026-06-21",
    totalAmount: 2280000,
    currency: "VND",
    holdExpiresAt: "2026-05-12T21:45:00+07:00",
    voidRetryRequired: false,
  },
  {
    id: "bk-1002",
    status: "CANCELLED",
    listingId: "lst-002",
    listingTitle: "Hyundai Santa Fe 2023",
    createdAt: "2026-05-10T10:00:00+07:00",
    pickupDate: "2026-06-10",
    returnDate: "2026-06-12",
    totalAmount: 2800000,
    currency: "VND",
    voidRetryRequired: false,
  },
  {
    id: "bk-1003",
    status: "EXPIRED",
    listingId: "lst-001",
    listingTitle: "Toyota Vios 2022",
    createdAt: "2026-05-01T10:00:00+07:00",
    pickupDate: "2026-05-28",
    returnDate: "2026-05-30",
    totalAmount: 1500000,
    currency: "VND",
    voidRetryRequired: false,
  },
  {
    id: "bk-1004",
    status: "CONFIRMED",
    listingId: "lst-002",
    listingTitle: "Hyundai Santa Fe 2023",
    createdAt: "2026-05-20T10:00:00+07:00",
    pickupDate: "2026-06-24",
    returnDate: "2026-06-27",
    totalAmount: 4050000,
    currency: "VND",
    voidRetryRequired: false,
  },
];

export const BOOKING_DETAILS: BookingDetailViewModel[] = [
  {
    id: "bk-1001",
    status: "HELD",
    listingId: "lst-001",
    listingTitle: "Toyota Vios 2022",
    createdAt: "2026-05-12T10:00:00+07:00",
    pickupDate: "2026-06-18",
    returnDate: "2026-06-21",
    pickupLocation: "District 7, Ho Chi Minh City",
    returnLocation: "Tan Son Nhat Airport",
    totalAmount: 2280000,
    currency: "VND",
    holdExpiresAt: "2026-05-12T21:45:00+07:00",
    voidRetryRequired: false,
    priceSnapshot: {
      rentalDays: 3,
      basePricePerDay: 700000,
      baseAmount: 2100000,
      extraAmount: 180000,
      totalAmount: 2280000,
      currency: "VND",
      extras: [
        {
          id: "ex-001",
          name: "Child Seat",
          quantity: 1,
          unitPrice: 120000,
          totalPrice: 120000,
          currency: "VND",
        },
        {
          id: "ex-004",
          name: "Fast Lane Pickup",
          quantity: 1,
          unitPrice: 60000,
          totalPrice: 60000,
          currency: "VND",
        },
      ],
    },
    policySnapshot: {
      cancellationPolicy: "FLEXIBLE",
      instantBook: false,
      dailyKmLimit: 200,
    },
  },
  {
    id: "bk-1002",
    status: "CANCELLED",
    listingId: "lst-002",
    listingTitle: "Hyundai Santa Fe 2023",
    createdAt: "2026-05-10T10:00:00+07:00",
    pickupDate: "2026-06-10",
    returnDate: "2026-06-12",
    pickupLocation: "Hai Chau District, Da Nang",
    returnLocation: "Hai Chau District, Da Nang",
    totalAmount: 2800000,
    currency: "VND",
    cancellationReason: "Change of plan",
    voidRetryRequired: false,
    priceSnapshot: {
      rentalDays: 2,
      basePricePerDay: 1250000,
      baseAmount: 2500000,
      extraAmount: 300000,
      totalAmount: 2800000,
      currency: "VND",
      extras: [
        {
          id: "ex-003",
          name: "Camping Kit",
          quantity: 1,
          unitPrice: 180000,
          totalPrice: 180000,
          currency: "VND",
        },
        {
          id: "ex-005",
          name: "Airport Delivery",
          quantity: 1,
          unitPrice: 120000,
          totalPrice: 120000,
          currency: "VND",
        },
      ],
    },
    policySnapshot: {
      cancellationPolicy: "MODERATE",
      instantBook: true,
      dailyKmLimit: 250,
    },
  },
  {
    id: "bk-1003",
    status: "EXPIRED",
    listingId: "lst-001",
    listingTitle: "Toyota Vios 2022",
    createdAt: "2026-05-01T10:00:00+07:00",
    pickupDate: "2026-05-28",
    returnDate: "2026-05-30",
    pickupLocation: "District 1, Ho Chi Minh City",
    returnLocation: "District 1, Ho Chi Minh City",
    totalAmount: 1500000,
    currency: "VND",
    voidRetryRequired: false,
    priceSnapshot: {
      rentalDays: 2,
      basePricePerDay: 700000,
      baseAmount: 1400000,
      extraAmount: 100000,
      totalAmount: 1500000,
      currency: "VND",
      extras: [
        {
          id: "ex-006",
          name: "Insurance Plus",
          quantity: 1,
          unitPrice: 100000,
          totalPrice: 100000,
          currency: "VND",
        },
      ],
    },
    policySnapshot: {
      cancellationPolicy: "FLEXIBLE",
      instantBook: false,
      dailyKmLimit: 200,
    },
  },
  {
    id: "bk-1004",
    status: "CONFIRMED",
    listingId: "lst-002",
    listingTitle: "Hyundai Santa Fe 2023",
    createdAt: "2026-05-20T10:00:00+07:00",
    pickupDate: "2026-06-24",
    returnDate: "2026-06-27",
    pickupLocation: "Da Nang International Airport",
    returnLocation: "Da Nang International Airport",
    totalAmount: 4050000,
    currency: "VND",
    voidRetryRequired: false,
    priceSnapshot: {
      rentalDays: 3,
      basePricePerDay: 1250000,
      baseAmount: 3750000,
      extraAmount: 300000,
      totalAmount: 4050000,
      currency: "VND",
      extras: [
        {
          id: "ex-003",
          name: "Camping Kit",
          quantity: 1,
          unitPrice: 180000,
          totalPrice: 180000,
          currency: "VND",
        },
        {
          id: "ex-005",
          name: "Airport Delivery",
          quantity: 1,
          unitPrice: 120000,
          totalPrice: 120000,
          currency: "VND",
        },
      ],
    },
    policySnapshot: {
      cancellationPolicy: "MODERATE",
      instantBook: true,
      dailyKmLimit: 250,
    },
  },
];

export function getBookingSummariesByStatus(
  status: BookingListFilterValue,
): BookingSummaryViewModel[] {
  if (status === "ALL") {
    return [...BOOKING_SUMMARIES];
  }
  return BOOKING_SUMMARIES.filter((booking) => booking.status === status);
}

export function getBookingDetailById(id: string): BookingDetailViewModel | null {
  const detail = BOOKING_DETAILS.find((item) => item.id === id);
  if (!detail) {
    return null;
  }
  return JSON.parse(JSON.stringify(detail)) as BookingDetailViewModel;
}
