import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { toastError } = vi.hoisted(() => ({
  toastError: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/host/listings",
}));

vi.mock("sonner", () => ({
  toast: {
    error: toastError,
    success: vi.fn(),
  },
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { HostListingsPageView } from "./host-listings-page-view";

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

describe("HostListingsPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    toastError.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders summary vehicle label and uses summary contract data", async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse({
        content: [
          {
            id: "lst-1",
            vehicleId: "vh-1",
            vehicleLabel: "Toyota Vios (2022)",
            title: "Xe di pho",
            city: "Hanoi",
            status: "ACTIVE",
            basePricePerDay: 700000,
            currency: "USD",
            createdAt: "2026-06-01T00:00:00Z",
          },
        ],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      }),
    );

    wrap(<HostListingsPageView />);

    expect(await screen.findByText(/Toyota Vios \(2022\)/i)).toBeInTheDocument();
    expect(screen.getByText(/Xe di pho/i)).toBeInTheDocument();
  });

  it("resumes a suspended listing and refetches the listing query", async () => {
    fetchSpy.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url === "/api/v1/host/listings?page=0&size=50&status=SUSPENDED") {
        return Promise.resolve(
          jsonResponse({
            content: [
              {
                id: "lst-1",
                vehicleId: "vh-1",
                vehicleLabel: "Toyota Vios (2022)",
                title: "Xe bi tam dung",
                city: "Hanoi",
                status: "SUSPENDED",
                basePricePerDay: 700000,
                currency: "VND",
                createdAt: "2026-06-01T00:00:00Z",
              },
            ],
            page: 0,
            size: 50,
            totalElements: 1,
            totalPages: 1,
          }),
        );
      }
      if (url === "/api/v1/host/listings/lst-1/resume" && init?.method === "POST") {
        return Promise.resolve(
          jsonResponse({
            id: "lst-1",
            vehicleId: "vh-1",
            hostId: "u-1",
            title: "Xe bi tam dung",
            description: "Mo ta",
            city: "Hanoi",
            address: "123 Main St",
            basePricePerDay: 700000,
            currency: "VND",
            dailyKmLimit: 200,
            instantBook: false,
            cancellationPolicy: "FLEXIBLE",
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
          }),
        );
      }
      return Promise.resolve(
        jsonResponse({
          content: [],
          page: 0,
          size: 50,
          totalElements: 0,
          totalPages: 0,
        }),
      );
    });

    const user = userEvent.setup();
    wrap(<HostListingsPageView />);

    await user.click(await screen.findByRole("button", { name: "Tam ngung" }));
    await user.click(await screen.findByRole("button", { name: "Kich hoat" }));

    await waitFor(() => {
      expect(fetchSpy).toHaveBeenCalledWith(
        "/api/v1/host/listings/lst-1/resume",
        expect.objectContaining({ method: "POST" }),
      );
    });
    await waitFor(() => {
      const suspendedCalls = fetchSpy.mock.calls.filter(
        ([url]) => String(url) === "/api/v1/host/listings?page=0&size=50&status=SUSPENDED",
      );
      expect(suspendedCalls.length).toBeGreaterThan(1);
    });
  });

  it("shows an error state when the listings query fails", async () => {
    fetchSpy.mockResolvedValue(jsonResponse({ code: "FAIL", message: "boom" }, 500));

    wrap(<HostListingsPageView />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/Khong tai duoc danh sach tin dang/i);
  });
});
