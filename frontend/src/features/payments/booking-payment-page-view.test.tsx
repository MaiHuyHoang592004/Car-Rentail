import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), refresh: vi.fn(), push: vi.fn() }),
  usePathname: () => "/bookings/bk-1/payment",
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { BookingPaymentPageView } from "./booking-payment-page-view";

const authedSession = {
  accessToken: "ACCESS",
  accessTokenExpiresAt: "2099-01-01T00:00:00Z",
  user: {
    id: "u-1",
    email: "u@e.com",
    emailVerified: true,
    roles: ["CUSTOMER"],
    fullName: "U",
    phone: null,
    dateOfBirth: null,
    addressLine: null,
    driverVerificationStatus: "APPROVED",
  },
};

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AuthProvider initialSession={authedSession}>
      <QueryClientProvider client={qc}>{node}</QueryClientProvider>
    </AuthProvider>,
  );
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const booking = {
  id: "bk-1",
  status: "HELD",
  listingId: "lst-1",
  listingTitle: "Toyota Vios 2022",
  customerId: "u-1",
  hostId: "h-1",
  pickupDate: "2026-06-01",
  returnDate: "2026-06-03",
  pickupLocation: "HCM",
  returnLocation: "HCM",
  holdExpiresAt: null,
  totalAmount: 1400000,
  currency: "VND",
  priceSnapshot: null,
  policySnapshot: null,
  voidRetryRequired: false,
  paymentRetryState: null,
  createdAt: "2026-06-01T00:00:00Z",
};

describe("BookingPaymentPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn((url: string) => {
      if (url === "/api/v1/bookings/bk-1") {
        return Promise.resolve(jsonResponse(booking));
      }
      if (url === "/api/v1/bookings/bk-1/payments") {
        return Promise.resolve(
          jsonResponse(
            { code: "BOOKING_NOT_FOUND", message: "Booking not found" },
            404,
          ),
        );
      }
      if (url === "/api/v1/payment-banks") {
        return Promise.resolve(jsonResponse({ items: [] }));
      }
      return Promise.reject(new Error(`Unexpected URL: ${url}`));
    });
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders payment lookup error instead of an empty payment state", async () => {
    wrap(<BookingPaymentPageView bookingId="bk-1" />);

    expect(await screen.findByText("BOOKING_NOT_FOUND")).toBeInTheDocument();
    expect(screen.getByText("Booking not found")).toBeInTheDocument();
    expect(
      screen.queryByText(/Chon ngan hang hoac phuong thuc de thanh toan/),
    ).not.toBeInTheDocument();
  });
});
