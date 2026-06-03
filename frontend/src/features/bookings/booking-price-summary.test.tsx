import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { BookingPriceSummary } from "./booking-price-summary";
import type { ListingDetailViewModel } from "@/features/listings/types";

const listing: ListingDetailViewModel = {
  id: "lst-1",
  title: "Toyota Vios 2022",
  description: "desc",
  city: "HCM",
  address: "123 Street",
  basePricePerDay: 700000,
  currency: "VND",
  dailyKmLimit: 200,
  instantBook: false,
  cancellationPolicy: "FLEXIBLE",
  status: "ACTIVE",
  coverImageUrl: "/car.jpg",
  galleryImageUrls: [],
  vehicle: {
    make: "Toyota",
    model: "Vios",
    year: 2022,
    category: "SEDAN",
    seats: 5,
    transmission: "AUTO",
    fuelType: "GASOLINE",
  },
  extras: [],
  availability: {
    from: "2026-06-01",
    to: "2026-06-30",
    days: [],
  },
};

describe("BookingPriceSummary", () => {
  it("uses date-only helper to compute rental days", () => {
    render(
      <BookingPriceSummary
        listing={listing}
        pickupDate="2026-06-01"
        returnDate="2026-06-03"
        selectedExtras={{}}
        onBook={vi.fn()}
        isPending={false}
        submitLabel="Đặt xe"
        helperText="helper"
      />,
    );

    expect(screen.getByText(/× 2 ngày/)).toBeInTheDocument();
  });
});
