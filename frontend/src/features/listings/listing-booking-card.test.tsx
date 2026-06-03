import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

import { ListingBookingCard } from "./listing-booking-card";
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

describe("ListingBookingCard", () => {
  it("shows estimated rental total using date-only day difference", async () => {
    render(<ListingBookingCard listing={listing} />);

    const inputs = screen.getAllByLabelText(/Nhận xe|Trả xe/);
    await userEvent.type(inputs[0], "2026-06-01");
    await userEvent.type(inputs[1], "2026-06-03");

    expect(await screen.findByText("Đơn giá thuê (2 ngày)")).toBeInTheDocument();
  });
});
