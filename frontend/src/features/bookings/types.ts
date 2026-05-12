export type BookingStatus =
  | "HELD"
  | "PENDING_HOST_APPROVAL"
  | "CONFIRMED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED"
  | "REJECTED"
  | "EXPIRED";

export type BookingListFilterValue = "ALL" | BookingStatus;

export type BookingListFilterState = {
  status: BookingListFilterValue;
};

export type BookingCreateFormState = {
  pickupDate: string;
  returnDate: string;
  pickupLocation: string;
  returnLocation: string;
  selectedExtraIds: string[];
};

export type BookingCreateFormErrors = Partial<
  Record<"pickupDate" | "returnDate" | "pickupLocation" | "returnLocation" | "form", string>
>;

export type BookingSummaryViewModel = {
  id: string;
  status: BookingStatus;
  listingId: string;
  listingTitle: string;
  pickupDate: string;
  returnDate: string;
  totalAmount: number;
  currency: "VND";
  holdExpiresAt?: string;
};

export type BookingPriceExtraViewModel = {
  id: string;
  name: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  currency: "VND";
};

export type BookingDetailViewModel = BookingSummaryViewModel & {
  pickupLocation: string;
  returnLocation: string;
  cancellationReason?: string;
  priceSnapshot: {
    rentalDays: number;
    basePricePerDay: number;
    baseAmount: number;
    extraAmount: number;
    totalAmount: number;
    currency: "VND";
    extras: BookingPriceExtraViewModel[];
  };
  policySnapshot: {
    cancellationPolicy: "FLEXIBLE" | "MODERATE" | "STRICT";
    instantBook: boolean;
    dailyKmLimit: number;
  };
};

export type BookingLocationPatchFormState = {
  pickupLocation: string;
  returnLocation: string;
};

export type BookingLocationPatchPayload = Partial<BookingLocationPatchFormState>;

export type CancelBookingFormState = {
  reason: string;
};
