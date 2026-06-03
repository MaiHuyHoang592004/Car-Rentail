import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/host/vehicles",
  useSearchParams: () => new URLSearchParams(),
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { HostVehiclesPageView } from "./host-vehicles-page-view";

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

function vehicle(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    category: "SEDAN",
    make: "Toyota",
    model: id === "vh-2" ? "Vios" : "Camry",
    year: 2022,
    transmission: "AUTO",
    fuelType: "PETROL",
    seats: 5,
    status: "ACTIVE",
    city: "Hanoi",
    plateNumber: "ABC-123",
    vin: "VIN123",
    identifierIntegrity: {
      plateNumberReadable: true,
      vinReadable: true,
      hasUnreadableEncryptedFields: false,
    },
    ...overrides,
  };
}

describe("HostVehiclesPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("switches filter and requests paginated vehicle data", async () => {
    fetchSpy.mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("status=ACTIVE")) {
        return Promise.resolve(
          jsonResponse({
            content: [vehicle("vh-2")],
            page: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
          }),
        );
      }
      return Promise.resolve(
        jsonResponse({
          content: [vehicle("vh-1", { status: "DRAFT" })],
          page: 0,
          size: 20,
          totalElements: 25,
          totalPages: 2,
        }),
      );
    });

    const user = userEvent.setup();
    wrap(<HostVehiclesPageView />);

    expect(await screen.findByText(/Toyota Camry/i)).toBeInTheDocument();
    expect(fetchSpy.mock.calls[0]?.[0]).toBe("/api/v1/host/vehicles?page=0&size=20");
    expect(screen.getByText(/Trang 1 \/ 2/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Hoat dong" }));

    expect(await screen.findByText(/Toyota Vios/i)).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchSpy).toHaveBeenCalledWith(
        "/api/v1/host/vehicles?page=0&size=20&status=ACTIVE",
        expect.any(Object),
      );
    });
  });

  it("shows empty state for an empty page", async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      }),
    );

    wrap(<HostVehiclesPageView />);

    expect(await screen.findByText(/Khong co xe nao o trang thai nay/i)).toBeInTheDocument();
  });

  it("shows a visible error state when the vehicle query fails", async () => {
    fetchSpy.mockResolvedValue(jsonResponse({ code: "FAIL", message: "boom" }, 500));

    wrap(<HostVehiclesPageView />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/Khong tai duoc danh sach xe/i);
  });
});
