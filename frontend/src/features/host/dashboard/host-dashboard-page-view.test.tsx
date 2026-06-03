import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/host/dashboard",
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { HostDashboardPageView } from "./host-dashboard-page-view";

const authedSession = {
  accessToken: "ACCESS",
  accessTokenExpiresAt: "2099-01-01T00:00:00Z",
  user: {
    id: "u-1",
    email: "host@rentflow.vn",
    emailVerified: true,
    roles: ["HOST"],
    fullName: "Host User",
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

describe("HostDashboardPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders dashboard sections when all queries succeed", async () => {
    fetchSpy.mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.startsWith("/api/v1/host/vehicles")) {
        return Promise.resolve(
          jsonResponse({
            content: [
              {
                id: "vh-1",
                category: "SEDAN",
                make: "Toyota",
                model: "Camry",
                year: 2022,
                transmission: "AUTO",
                fuelType: "PETROL",
                seats: 5,
                status: "MAINTENANCE",
                city: "Hanoi",
                plateNumber: "ABC-123",
                vin: "VIN123",
                identifierIntegrity: {
                  plateNumberReadable: true,
                  vinReadable: true,
                  hasUnreadableEncryptedFields: false,
                },
              },
            ],
            page: 0,
            size: 100,
            totalElements: 1,
            totalPages: 1,
          }),
        );
      }
      if (url.startsWith("/api/v1/host/listings")) {
        return Promise.resolve(
          jsonResponse({
            content: [
              {
                id: "lst-1",
                vehicleId: "vh-1",
                vehicleLabel: "Toyota Camry (2022)",
                title: "Xe di pho",
                city: "Hanoi",
                status: "PENDING_APPROVAL",
                basePricePerDay: 700000,
                currency: "VND",
                createdAt: "2026-06-01T00:00:00Z",
              },
            ],
            page: 0,
            size: 100,
            totalElements: 1,
            totalPages: 1,
          }),
        );
      }
      if (url.startsWith("/api/v1/host/bookings")) {
        return Promise.resolve(
          jsonResponse({
            content: [
              {
                id: "bk-1",
                status: "PENDING_HOST_APPROVAL",
                listingId: "lst-1",
                listingTitle: "Xe di pho",
                pickupDate: "2026-06-10",
                returnDate: "2026-06-12",
                holdExpiresAt: null,
                hostApprovalExpiresAt: null,
                totalAmount: 1400000,
                currency: "VND",
                createdAt: "2026-06-01T00:00:00Z",
              },
            ],
            page: 0,
            size: 5,
            totalElements: 1,
            totalPages: 1,
          }),
        );
      }
      return Promise.resolve(
        jsonResponse({
          from: "2026-05-06",
          to: "2026-06-04",
          grossCaptured: 1000000,
          netEarnings: 900000,
          bookingCount: 1,
          activeListings: 1,
          pendingApprovalListings: 1,
          blockedDays: 2,
          holdDays: 0,
          bookedDays: 3,
          generatedDays: 365,
          occupancyRate: 1.5,
          blockedRate: 0.5,
        }),
      );
    });

    wrap(<HostDashboardPageView />);

    expect(await screen.findByText(/Xe can xu ly/i)).toBeInTheDocument();
    expect(screen.getByText(/Tin dang can xu ly/i)).toBeInTheDocument();
    expect(screen.getByText(/Booking cho duyet/i)).toBeInTheDocument();
    expect(screen.queryByText(/Khong tai duoc bao cao tong quan/i)).not.toBeInTheDocument();
    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/host/listings?page=0&size=100"),
      expect.any(Object),
    );
  });

  it("keeps the page usable and shows safe fallback when overview fails", async () => {
    fetchSpy.mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.startsWith("/api/v1/host/vehicles")) {
        return Promise.resolve(
          jsonResponse({
            content: [],
            page: 0,
            size: 100,
            totalElements: 0,
            totalPages: 0,
          }),
        );
      }
      if (url.startsWith("/api/v1/host/listings")) {
        return Promise.resolve(
          jsonResponse({
            content: [
              {
                id: "lst-1",
                vehicleId: "vh-1",
                vehicleLabel: "Toyota Camry (2022)",
                title: "Xe di pho",
                city: "Hanoi",
                status: "PENDING_APPROVAL",
                basePricePerDay: 700000,
                currency: "VND",
                createdAt: "2026-06-01T00:00:00Z",
              },
            ],
            page: 0,
            size: 100,
            totalElements: 1,
            totalPages: 1,
          }),
        );
      }
      if (url.startsWith("/api/v1/host/bookings")) {
        return Promise.resolve(
          jsonResponse({
            content: [],
            page: 0,
            size: 5,
            totalElements: 0,
            totalPages: 0,
          }),
        );
      }
      return Promise.resolve(jsonResponse({ code: "FAIL", message: "boom" }, 500));
    });

    wrap(<HostDashboardPageView />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/So lieu tong hop dang dung fallback an toan/i);
    expect(screen.getByText(/Tin dang can xu ly/i)).toBeInTheDocument();
    expect(screen.getByText(/Xe di pho/i)).toBeInTheDocument();
  });
});
