import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  adminApproveListing,
  adminGetListingDetail,
  adminListListings,
  adminReactivateListing,
  adminRejectListing,
  adminSuspendListing,
} from "./api";

describe("admin listings api", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  }

  const rawSummaryPage = {
    content: [
      {
        id: "lst-1",
        title: "Toyota Vios 2022",
        city: "Hanoi",
        status: "PENDING_APPROVAL",
        basePricePerDay: 700000,
        currency: "VND",
        createdAt: "2026-06-01T00:00:00Z",
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  };

  const rawDetail = {
    listing: {
      id: "lst-1",
      vehicleId: "v-1",
      title: "Toyota Vios 2022",
      description: "A sedan",
      city: "Hanoi",
      address: "123 Main St",
      basePricePerDay: 700000,
      currency: "VND",
      dailyKmLimit: 200,
      instantBook: false,
      cancellationPolicy: "FLEXIBLE",
      status: "PENDING_APPROVAL",
      createdAt: "2026-06-01T00:00:00Z",
    },
    host: { id: "h-1", fullName: "Host Name", email: "host@example.com" },
    bookingSummary: { activeBookings: 0 },
  };

  /* ---------------------------------------------------------------- */
  /*  adminListListings                                               */
  /* ---------------------------------------------------------------- */

  describe("adminListListings", () => {
    it("sends filters and maps response", async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse(rawSummaryPage));
      const result = await adminListListings({
        status: "PENDING_APPROVAL",
        city: "Hanoi",
        page: 0,
        size: 20,
      });
      expect(result.listings).toHaveLength(1);
      expect(result.listings[0].id).toBe("lst-1");
      expect(result.listings[0].status).toBe("PENDING_APPROVAL");
      expect(result.totalElements).toBe(1);

      const url = fetchSpy.mock.calls[0][0] as string;
      expect(url).toContain("status=PENDING_APPROVAL");
      expect(url).toContain("city=Hanoi");
      expect(url).toContain("page=0");
      expect(url).toContain("size=20");
    });

    it("omits status param when ALL", async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse({ ...rawSummaryPage, content: [] }));
      await adminListListings({ status: "ALL" });
      const url = fetchSpy.mock.calls[0][0] as string;
      expect(url).not.toContain("status=");
    });
  });

  /* ---------------------------------------------------------------- */
  /*  adminGetListingDetail                                           */
  /* ---------------------------------------------------------------- */

  describe("adminGetListingDetail", () => {
    it("fetches detail and normalizes amounts", async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse(rawDetail));
      const result = await adminGetListingDetail("lst-1");
      expect(result.listing.id).toBe("lst-1");
      expect(result.listing.basePricePerDay).toBe(700000);
      expect(result.host?.fullName).toBe("Host Name");
      expect(result.bookingSummary.activeBookings).toBe(0);
      expect(fetchSpy.mock.calls[0][0]).toBe("/api/v1/admin/listings/lst-1");
    });
  });

  /* ---------------------------------------------------------------- */
  /*  adminApproveListing                                             */
  /* ---------------------------------------------------------------- */

  describe("adminApproveListing", () => {
    it("sends POST to approve endpoint and refreshes detail", async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse({
        id: "lst-1",
        status: "ACTIVE",
      }));
      fetchSpy.mockResolvedValueOnce(jsonResponse({ ...rawDetail, listing: { ...rawDetail.listing, status: "ACTIVE" } }));
      const result = await adminApproveListing("lst-1");
      expect(result.listing.status).toBe("ACTIVE");
      const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
      expect(url).toBe("/api/v1/admin/listings/lst-1/approve");
      expect(init.method).toBe("POST");
      expect(fetchSpy.mock.calls[1][0]).toBe("/api/v1/admin/listings/lst-1");
    });
  });

  /* ---------------------------------------------------------------- */
  /*  adminRejectListing                                              */
  /* ---------------------------------------------------------------- */

  describe("adminRejectListing", () => {
    it("sends reason in request body and refreshes detail", async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse({
        id: "lst-1",
        status: "DRAFT",
      }));
      fetchSpy.mockResolvedValueOnce(jsonResponse({ ...rawDetail, listing: { ...rawDetail.listing, status: "DRAFT" } }));
      const result = await adminRejectListing("lst-1", "Missing info");
      expect(result.listing.status).toBe("DRAFT");
      const [, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
      expect(JSON.parse(init.body as string)).toEqual({ reason: "Missing info" });
      expect(fetchSpy.mock.calls[1][0]).toBe("/api/v1/admin/listings/lst-1");
    });
  });

  /* ---------------------------------------------------------------- */
  /*  adminSuspendListing                                             */
  /* ---------------------------------------------------------------- */

  describe("adminSuspendListing", () => {
    it("sends reason in request body and refreshes detail", async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse({
        id: "lst-1",
        status: "SUSPENDED",
      }));
      fetchSpy.mockResolvedValueOnce(jsonResponse({ ...rawDetail, listing: { ...rawDetail.listing, status: "SUSPENDED" } }));
      const result = await adminSuspendListing("lst-1", "Policy violation");
      expect(result.listing.status).toBe("SUSPENDED");
      const [, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
      expect(JSON.parse(init.body as string)).toEqual({
        reason: "Policy violation",
        source: "ADMIN",
        suspensionUntil: null,
      });
      expect(fetchSpy.mock.calls[1][0]).toBe("/api/v1/admin/listings/lst-1");
    });
  });

  /* ---------------------------------------------------------------- */
  /*  adminReactivateListing                                          */
  /* ---------------------------------------------------------------- */

  describe("adminReactivateListing", () => {
    it("sends POST to reactivate endpoint and refreshes detail", async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse({
        id: "lst-1",
        status: "ACTIVE",
      }));
      fetchSpy.mockResolvedValueOnce(jsonResponse({ ...rawDetail, listing: { ...rawDetail.listing, status: "ACTIVE" } }));
      const result = await adminReactivateListing("lst-1");
      expect(result.listing.status).toBe("ACTIVE");
      expect(fetchSpy.mock.calls[0][0]).toBe("/api/v1/admin/listings/lst-1/reactivate");
      expect(fetchSpy.mock.calls[1][0]).toBe("/api/v1/admin/listings/lst-1");
    });
  });
});
