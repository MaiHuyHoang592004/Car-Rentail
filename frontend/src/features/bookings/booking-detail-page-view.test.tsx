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
  voidRetryRequired: false,
  paymentRetryState: null,
  createdAt: "2026-06-01T00:00:00Z",
};

const bookingPending = {
  ...bookingHELD,
  status: "PENDING_HOST_APPROVAL",
  holdExpiresAt: null,
};

const bookingConfirmedFuture = {
  ...bookingHELD,
  status: "CONFIRMED",
  pickupDate: "2099-01-02",
  returnDate: "2099-01-04",
  holdExpiresAt: null,
};

const bookingConfirmedPast = {
  ...bookingHELD,
  status: "CONFIRMED",
  pickupDate: "2020-01-02",
  returnDate: "2020-01-04",
  holdExpiresAt: null,
};

const cancelPreview = {
  eligible: true,
  refundableAmount: 1400000,
  penaltyAmount: 0,
  currency: "VND",
  policy: "FLEXIBLE",
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

  it("renders detail and Pay-now links to payment page", async () => {
    fetchSpy.mockResolvedValueOnce(jsonResponse(bookingHELD));
    wrap(<BookingDetailPageView bookingId="bk-1" />);
    await screen.findByText("Toyota Vios 2022");
    const payLink = screen.getByRole("link", { name: /Thanh toan/ });
    expect(payLink).toHaveAttribute("href", "/bookings/bk-1/payment");
  });

  it("PATCH locations shows success toast and refetches", async () => {
    fetchSpy
      .mockResolvedValueOnce(jsonResponse(bookingHELD))
      .mockResolvedValueOnce(jsonResponse({ ...bookingHELD, pickupLocation: "Da Nang" }))
      .mockResolvedValueOnce(jsonResponse({ ...bookingHELD, pickupLocation: "Da Nang" }));
    wrap(<BookingDetailPageView bookingId="bk-1" />);
    await screen.findByText("Toyota Vios 2022");

    await userEvent.click(screen.getByRole("button", { name: /Chinh dia diem/ }));
    const inputs = document.querySelectorAll<HTMLInputElement>('input[type="text"]');
    await userEvent.clear(inputs[0]);
    await userEvent.type(inputs[0], "Da Nang");
    await userEvent.click(screen.getByRole("button", { name: /Save locations/ }));

    await waitFor(() => expect(toastSuccess).toHaveBeenCalledWith("Da cap nhat dia diem"));
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
      .mockResolvedValueOnce(jsonResponse(cancelPreview))
      .mockResolvedValueOnce(
        jsonResponse({ id: "bk-1", status: "CANCELLED", cancellationReason: "test" }),
      )
      .mockResolvedValueOnce(jsonResponse({ ...bookingHELD, status: "CANCELLED" }));
    wrap(<BookingDetailPageView bookingId="bk-1" />);
    await screen.findByText("Toyota Vios 2022");

    await userEvent.click(screen.getByRole("button", { name: /Huy don/ }));
    expect(await screen.findByText("Hoan lai du kien")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Xác nhận hủy đơn/ }));

    await waitFor(() => expect(toastSuccess).toHaveBeenCalledWith("Da huy don"));
    const cancelCall = fetchSpy.mock.calls.find(([url]) =>
      typeof url === "string" && url.endsWith("/cancel"),
    );
    expect(cancelCall).toBeDefined();
    const headers = new Headers((cancelCall![1] as RequestInit).headers);
    expect(headers.get("Idempotency-Key")).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  it("shows cancel enabled for PENDING_HOST_APPROVAL", async () => {
    fetchSpy.mockResolvedValueOnce(jsonResponse(bookingPending));
    wrap(<BookingDetailPageView bookingId="bk-1" />);
    await screen.findByText("Toyota Vios 2022");
    expect(screen.getByRole("button", { name: /Huy don/ })).toBeEnabled();
  });

  it("shows cancel enabled for CONFIRMED before pickup date", async () => {
    fetchSpy.mockResolvedValueOnce(jsonResponse(bookingConfirmedFuture));
    wrap(<BookingDetailPageView bookingId="bk-1" />);
    await screen.findByText("Toyota Vios 2022");
    expect(screen.getByRole("button", { name: /Huy don/ })).toBeEnabled();
  });

  it("disables cancel for CONFIRMED after pickup date", async () => {
    fetchSpy.mockResolvedValueOnce(jsonResponse(bookingConfirmedPast));
    wrap(<BookingDetailPageView bookingId="bk-1" />);
    await screen.findByText("Toyota Vios 2022");
    expect(
      screen.getByText(/Don da den hoac qua ngay nhan xe, khong the huy/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Huy don/ })).not.toBeInTheDocument();
  });

  it("shows accepted-success message when cancel requires background void retry", async () => {
    fetchSpy
      .mockResolvedValueOnce(jsonResponse(bookingPending))
      .mockResolvedValueOnce(jsonResponse(cancelPreview))
      .mockResolvedValueOnce(
        jsonResponse(
          {
            id: "bk-1",
            status: "CANCELLED",
            cancellationReason: "test",
            cancellationCompleted: true,
            voidRetryRequired: true,
            code: "PAYMENT_VOID_RETRY_REQUIRED",
            paymentRetryState: "VOID_RETRY_REQUIRED",
          },
          202,
        ),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          ...bookingPending,
          status: "CANCELLED",
          voidRetryRequired: true,
          paymentRetryState: "VOID_RETRY_REQUIRED",
        }),
      );
    wrap(<BookingDetailPageView bookingId="bk-1" />);
    await screen.findByText("Toyota Vios 2022");

    await userEvent.click(screen.getByRole("button", { name: /Huy don/ }));
    expect(await screen.findByText("Hoan lai du kien")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Xác nhận hủy đơn/ }));

    await waitFor(() =>
      expect(toastSuccess).toHaveBeenCalledWith(
        "Da huy don; hoan tien se duoc xu ly tiep",
      ),
    );
    expect(
      await screen.findByText(/Don da bi huy, nhung thanh toan van dang duoc xu ly/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/He thong dang retry thao tac void thanh toan trong nen/),
    ).toBeInTheDocument();
  });

  it("renders persistent retry-state banner from booking detail data", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        ...bookingPending,
        status: "CANCELLED",
        voidRetryRequired: true,
        paymentRetryState: "VOID_RETRY_REQUIRED",
      }),
    );
    wrap(<BookingDetailPageView bookingId="bk-1" />);

    expect(
      await screen.findByText(/Don da bi huy, nhung thanh toan van dang duoc xu ly/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/He thong dang retry thao tac void thanh toan trong nen/),
    ).toBeInTheDocument();
  });

  it("renders manual-support banner when void retry is exhausted", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        ...bookingPending,
        status: "CANCELLED",
        voidRetryRequired: false,
        paymentRetryState: "VOID_RETRY_EXHAUSTED",
        paymentStatus: "AUTHORIZED",
        voidRetryLastError: "provider down",
        voidRetryCount: 3,
      }),
    );
    wrap(<BookingDetailPageView bookingId="bk-1" />);

    expect(
      await screen.findByText("Thanh toán cần hỗ trợ xử lý thủ công"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Hệ thống đã hết lượt retry thao tác thanh toán/),
    ).toBeInTheDocument();
    expect(screen.getByText(/provider down/)).toBeInTheDocument();
  });

  it("dialog cancel uses Vietnamese copy", async () => {
    fetchSpy
      .mockResolvedValueOnce(jsonResponse(bookingHELD))
      .mockResolvedValueOnce(jsonResponse(cancelPreview));
    wrap(<BookingDetailPageView bookingId="bk-1" />);
    await screen.findByText("Toyota Vios 2022");

    await userEvent.click(screen.getByRole("button", { name: /Huy don/ }));

    expect(await screen.findByRole("heading", { name: "Xác nhận hủy đơn" })).toBeInTheDocument();
    expect(
      screen.getByText("Lý do hủy đơn (không bắt buộc, tối đa 500 ký tự)"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Giữ lại đơn" })).toBeInTheDocument();
  });

  it("preview failure closes cancel dialog, refetches detail, and reports clear error", async () => {
    fetchSpy
      .mockResolvedValueOnce(jsonResponse(bookingConfirmedFuture))
      .mockResolvedValueOnce(
        jsonResponse(
          {
            code: "BOOKING_INVALID_STATUS",
            message: "Booking cannot be cancelled now",
          },
          409,
        ),
      )
      .mockResolvedValueOnce(jsonResponse({ ...bookingConfirmedFuture, status: "IN_PROGRESS" }));

    wrap(<BookingDetailPageView bookingId="bk-1" />);
    await screen.findByText("Toyota Vios 2022");

    await userEvent.click(screen.getByRole("button", { name: /Huy don/ }));

    await waitFor(() =>
      expect(toastError).toHaveBeenCalledWith(
        "Booking không còn ở trạng thái có thể hủy. Đang tải lại chi tiết.",
      ),
    );
    expect(screen.queryByRole("heading", { name: "Xác nhận hủy đơn" })).not.toBeInTheDocument();
    await waitFor(() => {
      const detailCalls = fetchSpy.mock.calls.filter(([url]) =>
        url === "/api/v1/bookings/bk-1",
      );
      expect(detailCalls).toHaveLength(2);
    });
  });
});
