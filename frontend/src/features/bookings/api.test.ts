import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  buildCreateBookingPayload,
  cancelBooking,
  createBooking,
  getBookingById,
  getCancelPreview,
  listMyBookings,
  patchBookingLocations,
} from "./api";

describe("buildCreateBookingPayload", () => {
  it("maps selected extras to extras[{extraId, quantity}]", () => {
    const payload = buildCreateBookingPayload({
      listingId: "lst-1",
      pickupDate: "2026-06-01",
      returnDate: "2026-06-03",
      pickupLocation: "  HCM  ",
      returnLocation: "",
      selectedExtras: [
        { extraId: "ex-1", quantity: 2 },
        { extraId: "ex-2", quantity: 1 },
      ],
    });
    expect(payload.extras).toEqual([
      { extraId: "ex-1", quantity: 2 },
      { extraId: "ex-2", quantity: 1 },
    ]);
    expect(payload.pickupLocation).toBe("HCM");
    expect(payload.returnLocation).toBeNull();
  });

  it("returns empty extras for empty selection", () => {
    const payload = buildCreateBookingPayload({
      listingId: "lst-1",
      pickupDate: "2026-06-01",
      returnDate: "2026-06-03",
      selectedExtras: [],
    });
    expect(payload.extras).toEqual([]);
  });
});

describe("booking api network calls", () => {
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

  const rawBooking = {
    id: "bk-1",
    status: "HELD",
    listingId: "lst-1",
    listingTitle: "Toyota Vios",
    customerId: "u-1",
    hostId: "h-1",
    pickupDate: "2026-06-01",
    returnDate: "2026-06-03",
    pickupLocation: "HCM",
    returnLocation: "HCM",
    holdExpiresAt: "2026-06-01T00:15:00Z",
    totalAmount: 1400000,
    currency: "VND",
    priceSnapshot: {
      rentalDays: 2,
      basePricePerDay: 700000,
      baseAmount: 1400000,
      extraAmount: 0,
      totalAmount: 1400000,
      currency: "VND",
      extras: [],
    },
    policySnapshot: { cancellationPolicy: "FLEXIBLE", instantBook: false, dailyKmLimit: 200 },
    paymentStatus: "AUTHORIZED",
    voidRetryLastError: "provider unavailable",
    voidRetryCount: 3,
    createdAt: "2026-06-01T00:00:00Z",
  };

  it("createBooking sets Idempotency-Key header and maps response", async () => {
    fetchSpy.mockResolvedValueOnce(jsonResponse(rawBooking, 201));
    const result = await createBooking(
      {
        listingId: "lst-1",
        pickupDate: "2026-06-01",
        returnDate: "2026-06-03",
        selectedExtras: [],
      },
      "deadbeef-1234-4567-8901-abcdef012345",
    );
    expect(result.id).toBe("bk-1");
    expect(result.currency).toBe("VND");
    expect(result.priceSnapshot.rentalDays).toBe(2);
    const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/bookings");
    expect(init.method).toBe("POST");
    const headers = new Headers(init.headers);
    expect(headers.get("Idempotency-Key")).toBe("deadbeef-1234-4567-8901-abcdef012345");
  });

  it("getBookingById hits GET /bookings/:id and parses snapshots safely", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({ ...rawBooking, priceSnapshot: null, policySnapshot: null }),
    );
    const result = await getBookingById("bk-1");
    expect(result.priceSnapshot.extras).toEqual([]);
    expect(result.policySnapshot.cancellationPolicy).toBe("FLEXIBLE");
    expect(result.paymentStatus).toBe("AUTHORIZED");
    expect(result.voidRetryLastError).toBe("provider unavailable");
    expect(result.voidRetryCount).toBe(3);
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/v1/bookings/bk-1");
  });

  it("getCancelPreview hits canonical preview endpoint and maps amounts", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        eligible: true,
        refundableAmount: "1200000",
        penaltyAmount: "200000",
        currency: "VND",
        policy: "MODERATE",
      }),
    );

    const result = await getCancelPreview("bk-1");

    expect(result).toEqual({
      eligible: true,
      refundableAmount: 1200000,
      penaltyAmount: 200000,
      currency: "VND",
      policy: "MODERATE",
    });
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/v1/bookings/bk-1/cancel-preview");
  });

  it("listMyBookings drops status when ALL and maps content", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        content: [
          {
            id: "bk-1",
            status: "HELD",
            listingId: "lst-1",
            listingTitle: "Vios",
            pickupDate: "2026-06-01",
            returnDate: "2026-06-03",
            holdExpiresAt: null,
            totalAmount: "1400000",
            currency: "VND",
            voidRetryRequired: true,
            paymentRetryState: "VOID_RETRY_REQUIRED",
            paymentStatus: "AUTHORIZED",
            voidRetryLastError: "void failed",
            voidRetryCount: 1,
            createdAt: "2026-06-01T00:00:00Z",
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    );
    const result = await listMyBookings({ status: "ALL", page: 0 });
    expect(result.content[0].totalAmount).toBe(1400000);
    expect(result.content[0].voidRetryRequired).toBe(true);
    expect(result.content[0].paymentRetryState).toBe("VOID_RETRY_REQUIRED");
    expect(result.content[0].paymentStatus).toBe("AUTHORIZED");
    expect(result.content[0].voidRetryLastError).toBe("void failed");
    expect(result.content[0].voidRetryCount).toBe(1);
    expect(result.content[0].createdAt).toBe("2026-06-01T00:00:00Z");
    const url = fetchSpy.mock.calls[0][0] as string;
    expect(url).not.toContain("status=");
    expect(url).toContain("page=0");
  });

  it("listMyBookings includes status filter when not ALL", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    );
    await listMyBookings({ status: "HELD", page: 1, size: 10 });
    const url = fetchSpy.mock.calls[0][0] as string;
    expect(url).toContain("status=HELD");
    expect(url).toContain("page=1");
    expect(url).toContain("size=10");
  });

  it("patchBookingLocations sends only provided fields", async () => {
    fetchSpy.mockResolvedValueOnce(jsonResponse(rawBooking));
    await patchBookingLocations("bk-1", { pickupLocation: "Da Nang" });
    const [, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body as string)).toEqual({ pickupLocation: "Da Nang" });
  });

  it("cancelBooking forwards idempotency key and reason", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        id: "bk-1",
        status: "CANCELLED",
        cancellationReason: "change of plan",
        cancellationCompleted: true,
        voidRetryRequired: true,
        code: "PAYMENT_VOID_RETRY_REQUIRED",
        paymentRetryState: "VOID_RETRY_REQUIRED",
      }),
    );
    const result = await cancelBooking(
      "bk-1",
      { reason: "change of plan" },
      "11111111-1111-4111-8111-111111111111",
    );
    expect(result.status).toBe("CANCELLED");
    expect(result.cancellationCompleted).toBe(true);
    expect(result.voidRetryRequired).toBe(true);
    expect(result.code).toBe("PAYMENT_VOID_RETRY_REQUIRED");
    expect(result.paymentRetryState).toBe("VOID_RETRY_REQUIRED");
    const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/bookings/bk-1/cancel");
    const headers = new Headers(init.headers);
    expect(headers.get("Idempotency-Key")).toBe("11111111-1111-4111-8111-111111111111");
    expect(JSON.parse(init.body as string)).toEqual({ reason: "change of plan" });
  });
});
