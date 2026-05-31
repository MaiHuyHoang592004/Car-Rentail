import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/host/vehicles/vh-1",
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { HostVehicleDetailPageView } from "./host-vehicle-detail-page-view";

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

describe("HostVehicleDetailPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows integrity warning and placeholders when identifiers are unreadable", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        id: "vh-1",
        category: "SEDAN",
        make: "Toyota",
        model: "Vios",
        year: 2022,
        transmission: "AUTO",
        fuelType: "PETROL",
        seats: 5,
        status: "ACTIVE",
        city: "Hanoi",
        plateNumber: null,
        vin: null,
        identifierIntegrity: {
          plateNumberReadable: false,
          vinReadable: false,
          hasUnreadableEncryptedFields: true,
        },
        photos: [],
      }),
    );

    wrap(<HostVehicleDetailPageView vehicleId="vh-1" />);

    expect(
      await screen.findByText(/Mot so du lieu dinh danh cua xe hien khong the doc duoc/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/Khong the doc bien so hien tai/i)).toBeInTheDocument();
    expect(screen.getByText(/Khong the doc VIN hien tai/i)).toBeInTheDocument();
  });

  it("renders readable identifiers without the warning banner", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        id: "vh-1",
        category: "SEDAN",
        make: "Toyota",
        model: "Vios",
        year: 2022,
        transmission: "AUTO",
        fuelType: "PETROL",
        seats: 5,
        status: "ACTIVE",
        city: "Hanoi",
        plateNumber: "51H-123.45",
        vin: "VIN123",
        identifierIntegrity: {
          plateNumberReadable: true,
          vinReadable: true,
          hasUnreadableEncryptedFields: false,
        },
        photos: [],
      }),
    );

    wrap(<HostVehicleDetailPageView vehicleId="vh-1" />);

    expect(await screen.findByText(/51H-123.45/i)).toBeInTheDocument();
    expect(screen.getByText(/VIN123/i)).toBeInTheDocument();
    expect(
      screen.queryByText(/Mot so du lieu dinh danh cua xe hien khong the doc duoc/i),
    ).not.toBeInTheDocument();
  });
});
