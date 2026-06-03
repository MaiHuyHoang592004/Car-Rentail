import { describe, expect, it } from "vitest";

import { mapListingDetail, mapListingSummary } from "./api";

describe("host listings api mappers", () => {
  it("maps listing summary without synthetic detail defaults", () => {
    const summary = mapListingSummary({
      id: "lst-1",
      vehicleId: "vh-1",
      vehicleLabel: "Toyota Vios (2022)",
      title: "Xe di pho",
      city: "Hanoi",
      status: "ACTIVE",
      basePricePerDay: "700000",
      currency: "USD",
      createdAt: "2026-06-01T00:00:00Z",
    });

    expect(summary).toEqual({
      id: "lst-1",
      vehicleId: "vh-1",
      vehicleLabel: "Toyota Vios (2022)",
      title: "Xe di pho",
      city: "Hanoi",
      status: "ACTIVE",
      basePricePerDay: 700000,
      currency: "USD",
    });
    expect("description" in summary).toBe(false);
    expect("instantBook" in summary).toBe(false);
  });

  it("maps listing detail and keeps backend currency", () => {
    const detail = mapListingDetail({
      id: "lst-1",
      vehicleId: "vh-1",
      hostId: "host-1",
      title: "Xe di pho",
      description: "Mo ta",
      city: "Hanoi",
      address: "123 Main St",
      basePricePerDay: "700000",
      currency: "EUR",
      dailyKmLimit: 200,
      instantBook: true,
      cancellationPolicy: "MODERATE",
      status: "ACTIVE",
      vehicleSummary: {
        category: "SEDAN",
        make: "Toyota",
        model: "Vios",
        year: 2022,
        transmission: "AUTO",
        fuelType: "PETROL",
        seats: 5,
        status: "ACTIVE",
      },
      extras: [],
      createdAt: "2026-06-01T00:00:00Z",
      updatedAt: "2026-06-01T00:00:00Z",
    });

    expect(detail.currency).toBe("EUR");
    expect(detail.vehicleLabel).toBe("Toyota Vios (2022)");
    expect(detail.instantBook).toBe(true);
  });
});
