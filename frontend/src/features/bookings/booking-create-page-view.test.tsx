import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const routerPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), refresh: vi.fn(), push: routerPush }),
  usePathname: () => "/listings/lst-001/book",
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { BookingCreatePageView } from "./booking-create-page-view";

const authedSession = {
  accessToken: "ACCESS",
  accessTokenExpiresAt: "2099-01-01T00:00:00Z",
  user: {
    id: "u-1",
    email: "u@e.com",
    emailVerified: false,
    roles: ["CUSTOMER"],
    fullName: "U",
    phone: null,
    dateOfBirth: null,
    addressLine: null,
    driverVerificationStatus: "NOT_SUBMITTED",
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

function listingResponse(): Response {
  return jsonResponse({
    id: "lst-001",
    title: "Toyota Vios 2022",
    description: "Listing detail",
    city: "HCM",
    address: "District 1",
    basePricePerDay: 700000,
    currency: "VND",
    dailyKmLimit: 200,
    instantBook: true,
    cancellationPolicy: "FLEXIBLE",
    photos: [],
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
  });
}

function isoOffset(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

describe("BookingCreatePageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    routerPush.mockClear();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("submits booking and redirects to detail on success", async () => {
    const pickup = isoOffset(1);
    const ret = isoOffset(3);
    fetchSpy.mockResolvedValueOnce(listingResponse());
    fetchSpy.mockResolvedValueOnce(
      jsonResponse(
        {
          id: "bk-99",
          status: "HELD",
          listingId: "lst-001",
          listingTitle: "Toyota Vios 2022",
          customerId: "u-1",
          hostId: "h-1",
          pickupDate: pickup,
          returnDate: ret,
          pickupLocation: "HCM",
          returnLocation: "HCM",
          holdExpiresAt: "2099-01-01T00:15:00Z",
          totalAmount: 1400000,
          currency: "VND",
          priceSnapshot: null,
          policySnapshot: null,
          createdAt: "2099-01-01T00:00:00Z",
        },
        201,
      ),
    );
    wrap(<BookingCreatePageView listingId="lst-001" isGuest={false} />);
    await screen.findByText(/Dat xe/);

    const dateInputs = document.querySelectorAll<HTMLInputElement>('input[type="date"]');
    await userEvent.type(dateInputs[0], pickup);
    await userEvent.type(dateInputs[1], ret);
    await userEvent.click(screen.getByRole("button", { name: /Giữ xe trong 15 phút/ }));

    await waitFor(() => expect(routerPush).toHaveBeenCalledWith("/bookings/bk-99"));
    const [url, init] = fetchSpy.mock.calls[1] as [string, RequestInit];
    expect(url).toBe("/api/v1/bookings");
    expect(init.method).toBe("POST");
    const headers = new Headers(init.headers);
    expect(headers.get("Idempotency-Key")).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  it("shows overlap banner on 409 BOOKING_OVERLAP_CUSTOMER", async () => {
    fetchSpy.mockResolvedValueOnce(listingResponse());
    fetchSpy.mockResolvedValueOnce(
      jsonResponse(
        {
          code: "BOOKING_OVERLAP_CUSTOMER",
          message: "Bạn đã có booking trùng thời gian.",
        },
        409,
      ),
    );
    wrap(<BookingCreatePageView listingId="lst-001" isGuest={false} />);
    await screen.findByText(/Dat xe/);

    const dateInputs = document.querySelectorAll<HTMLInputElement>('input[type="date"]');
    await userEvent.type(dateInputs[0], isoOffset(1));
    await userEvent.type(dateInputs[1], isoOffset(3));
    await userEvent.click(screen.getByRole("button", { name: /Giữ xe trong 15 phút/ }));

    await waitFor(() => expect(screen.getByText("Trung booking")).toBeInTheDocument());
    expect(routerPush).not.toHaveBeenCalled();
  });

  it("blocks submit with validation when return ≤ pickup", async () => {
    fetchSpy.mockResolvedValueOnce(listingResponse());
    wrap(<BookingCreatePageView listingId="lst-001" isGuest={false} />);
    await screen.findByText(/Dat xe/);
    const sameDay = isoOffset(1);
    const dateInputs = document.querySelectorAll<HTMLInputElement>('input[type="date"]');
    await userEvent.type(dateInputs[0], sameDay);
    await userEvent.type(dateInputs[1], sameDay);
    await userEvent.click(screen.getByRole("button", { name: /Giữ xe trong 15 phút/ }));

    expect(screen.getByText("Ngày trả phải sau ngày nhận.")).toBeInTheDocument();
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it("shows verify-email CTA on 403 EMAIL_NOT_VERIFIED", async () => {
    fetchSpy.mockResolvedValueOnce(listingResponse());
    fetchSpy.mockResolvedValueOnce(
      jsonResponse(
        {
          code: "EMAIL_NOT_VERIFIED",
          message: "Email is not verified",
        },
        403,
      ),
    );
    wrap(<BookingCreatePageView listingId="lst-001" isGuest={false} />);
    await screen.findByText(/Dat xe/);

    const dateInputs = document.querySelectorAll<HTMLInputElement>('input[type="date"]');
    await userEvent.type(dateInputs[0], isoOffset(1));
    await userEvent.type(dateInputs[1], isoOffset(3));
    await userEvent.click(screen.getByRole("button", { name: /Giữ xe trong 15 phút/ }));

    await waitFor(() =>
      expect(screen.getByText("Email chua duoc xac minh")).toBeInTheDocument(),
    );
    expect(
      screen.getByRole("link", { name: /Xac minh email/ }),
    ).toHaveAttribute("href", "/me/profile");
  });
});
