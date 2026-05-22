import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), refresh: vi.fn(), push: vi.fn() }),
  usePathname: () => "/bookings/bk-1",
}));

const toastSuccess = vi.fn();
const toastError = vi.fn();
const toastMessage = vi.fn();
vi.mock("sonner", () => ({
  toast: {
    success: (msg: string) => toastSuccess(msg),
    error: (msg: string) => toastError(msg),
    message: (msg: string) => toastMessage(msg),
  },
  Toaster: () => null,
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { BookingDetailPageView } from "./booking-detail-page-view";

const authedSession = {
  accessToken: "ACCESS",
  accessTokenExpiresAt: "2099-01-01T00:00:00Z",
  user: {
    id: "u-1",
    email: "u@e.com",
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

const bookingHELD = {
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
  holdExpiresAt: "2099-01-01T00:15:00Z",
  totalAmount: 1400000,
  currency: "VND",
  priceSnapshot: null,
  policySnapshot: null,
  createdAt: "2026-06-01T00:00:00Z",
};

describe("BookingDetailPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    toastSuccess.mockClear();
    toastError.mockClear();
    toastMessage.mockClear();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders detail and Pay-now disabled with tooltip", async () => {
    fetchSpy.mockResolvedValueOnce(jsonResponse(bookingHELD));
    wrap(<BookingDetailPageView bookingId="bk-1" />);
    await screen.findByText("Toyota Vios 2022");
    const payButton = screen.getByRole("button", { name: "Pay now" });
    expect(payButton).toBeDisabled();
    expect(payButton.getAttribute("title")).toContain("Thanh toán sẽ sớm có mặt");
  });

  it("PATCH locations shows success toast and refetches", async () => {
    fetchSpy
      .mockResolvedValueOnce(jsonResponse(bookingHELD))
      .mockResolvedValueOnce(jsonResponse({ ...bookingHELD, pickupLocation: "Da Nang" }))
      .mockResolvedValueOnce(jsonResponse({ ...bookingHELD, pickupLocation: "Da Nang" }));
    wrap(<BookingDetailPageView bookingId="bk-1" />);
    await screen.findByText("Toyota Vios 2022");

    await userEvent.click(screen.getByRole("button", { name: /Chỉnh địa điểm/ }));
    const inputs = document.querySelectorAll<HTMLInputElement>('input[type="text"]');
    await userEvent.clear(inputs[0]);
    await userEvent.type(inputs[0], "Da Nang");
    await userEvent.click(screen.getByRole("button", { name: /Save locations/ }));

    await waitFor(() => expect(toastSuccess).toHaveBeenCalledWith("Đã cập nhật địa điểm"));
    const patchCall = fetchSpy.mock.calls.find(([url, init]) => (init as RequestInit)?.method === "PATCH");
    expect(patchCall).toBeDefined();
    expect((patchCall![0] as string)).toBe("/api/v1/bookings/bk-1");
    expect(JSON.parse((patchCall![1] as RequestInit).body as string)).toEqual({
      pickupLocation: "Da Nang",
      returnLocation: "HCM",
    });
  });

  it("Cancel HELD attaches Idempotency-Key and refetches", async () => {
    fetchSpy
      .mockResolvedValueOnce(jsonResponse(bookingHELD))
      .mockResolvedValueOnce(
        jsonResponse({ id: "bk-1", status: "CANCELLED", cancellationReason: "test" }),
      )
      .mockResolvedValueOnce(jsonResponse({ ...bookingHELD, status: "CANCELLED" }));
    wrap(<BookingDetailPageView bookingId="bk-1" />);
    await screen.findByText("Toyota Vios 2022");

    await userEvent.click(screen.getByRole("button", { name: /Hủy booking/ }));
    await userEvent.click(screen.getByRole("button", { name: /Confirm cancel/ }));

    await waitFor(() => expect(toastSuccess).toHaveBeenCalledWith("Đã hủy booking"));
    const cancelCall = fetchSpy.mock.calls.find(([url]) =>
      typeof url === "string" && url.endsWith("/cancel"),
    );
    expect(cancelCall).toBeDefined();
    const headers = new Headers((cancelCall![1] as RequestInit).headers);
    expect(headers.get("Idempotency-Key")).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });
});
