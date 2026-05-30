import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  DEFAULT_LISTING_FILTERS,
  buildListingSearchQuery,
  mapListingSearchResponse,
  searchListings,
} from "./api";

describe("listing search api", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function jsonResponse(body: unknown): Response {
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }

  it("builds backend query params and skips empty ALL filters", () => {
    const query = buildListingSearchQuery(
      {
        ...DEFAULT_LISTING_FILTERS,
        city: " Hanoi ",
        pickupDate: "2026-07-01",
        returnDate: "2026-07-03",
        category: "SUV",
        transmission: "AUTO",
        fuelType: "EV",
        seats: "5",
        minPrice: "500000",
        maxPrice: "2000000",
      },
      2,
      20,
    );
    const params = new URLSearchParams(query);
    expect(params.get("city")).toBe("Hanoi");
    expect(params.get("categories")).toBe("SUV");
    expect(params.get("pickupDate")).toBe("2026-07-01");
    expect(params.get("returnDate")).toBe("2026-07-03");
    expect(params.get("transmission")).toBe("AUTO");
    expect(params.get("fuelType")).toBe("EV");
    expect(params.get("seats")).toBe("5");
    expect(params.get("minPrice")).toBe("500000");
    expect(params.get("maxPrice")).toBe("2000000");
    expect(params.get("sort")).toBe("NEWEST");
    expect(params.get("page")).toBe("2");
    expect(params.get("size")).toBe("20");
  });

  it("omits default filters", () => {
    const params = new URLSearchParams(buildListingSearchQuery(DEFAULT_LISTING_FILTERS));
    expect(params.has("city")).toBe(false);
    expect(params.has("categories")).toBe(false);
    expect(params.has("transmission")).toBe(false);
    expect(params.has("fuelType")).toBe(false);
    expect(params.get("sort")).toBe("NEWEST");
    expect(params.get("page")).toBe("0");
    expect(params.get("size")).toBe("20");
  });

  it("maps backend search result to listing card view model", () => {
    const card = mapListingSearchResponse({
      id: "lst-1",
      title: "Toyota Vios",
      city: "Hanoi",
      category: "SEDAN",
      basePricePerDay: "700000.00",
      currency: "VND",
      seats: 5,
      transmission: "AUTO",
      fuelType: "GASOLINE",
      coverPhotoUrl: null,
      ratingAverage: "4.75",
    });
    expect(card).toMatchObject({
      id: "lst-1",
      title: "Toyota Vios",
      city: "Hanoi",
      category: "SEDAN",
      basePricePerDay: 700000,
      currency: "VND",
      seats: 5,
      transmission: "AUTO",
      fuelType: "GASOLINE",
      status: "ACTIVE",
      ratingLabel: "4.8 rating",
    });
    expect(card.coverImageUrl).toContain("images.unsplash.com");
  });

  it("calls GET /listings with search params and maps page", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        content: [
          {
            id: "lst-1",
            title: "Toyota Vios",
            city: "Hanoi",
            category: "SEDAN",
            basePricePerDay: 700000,
            currency: "VND",
            seats: 5,
            transmission: "AUTO",
            fuelType: "GASOLINE",
            coverPhotoUrl: null,
            ratingAverage: null,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    );

    const page = await searchListings({ ...DEFAULT_LISTING_FILTERS, city: "Hanoi" });

    expect(page.content[0].title).toBe("Toyota Vios");
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/v1/listings?city=Hanoi&sort=NEWEST&page=0&size=20");
  });
});
