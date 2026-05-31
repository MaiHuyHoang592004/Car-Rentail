import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/host/listings/lst-1",
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { HostListingDetailPageView } from "./host-listing-detail-page-view";

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

function listingDetail(overrides: Record<string, unknown> = {}) {
  return {
    id: "lst-1",
    vehicleId: "vh-1",
    hostId: "u-1",
    title: "Toyota Vios Listing",
    description: "Sedan listing",
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
    ...overrides,
  };
}

describe("HostListingDetailPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows explicit suspension reason, source, and until when metadata exists", async () => {
    fetchSpy
      .mockResolvedValueOnce(
        jsonResponse(
          listingDetail({
            status: "SUSPENDED",
            suspensionReason: "Missing registration proof",
            suspensionSource: "ADMIN_REVIEW",
            suspensionUntil: "2026-06-15T10:30:00Z",
          }),
        ),
      )
      .mockResolvedValueOnce(jsonResponse([]));

    wrap(<HostListingDetailPageView listingId="lst-1" />);

    expect(await screen.findByText(/Ly do tam ngung:/i)).toBeInTheDocument();
    expect(screen.getByText(/Missing registration proof/i)).toBeInTheDocument();
    expect(screen.getByText(/Nguon tam ngung:/i)).toBeInTheDocument();
    expect(screen.getByText(/ADMIN_REVIEW/i)).toBeInTheDocument();
    expect(screen.getByText(/Hieu luc den:/i)).toBeInTheDocument();
  });

  it("shows fallback guidance when a suspended listing has no metadata", async () => {
    fetchSpy
      .mockResolvedValueOnce(
        jsonResponse(
          listingDetail({
            status: "SUSPENDED",
            suspensionReason: null,
            suspensionSource: null,
            suspensionUntil: null,
          }),
        ),
      )
      .mockResolvedValueOnce(jsonResponse([]));

    wrap(<HostListingDetailPageView listingId="lst-1" />);

    expect(
      await screen.findByText(
        /Trang thai tam ngung dang co hieu luc, nhung he thong khong co them thong tin ly do hoac thoi han./i,
      ),
    ).toBeInTheDocument();
  });

  it("keeps the existing active guidance unchanged", async () => {
    fetchSpy
      .mockResolvedValueOnce(jsonResponse(listingDetail()))
      .mockResolvedValueOnce(jsonResponse([]));

    wrap(<HostListingDetailPageView listingId="lst-1" />);

    expect(
      await screen.findByText(
        /Listing dang ACTIVE\. Neu muon sua noi dung hoac extras, hay luu kho listing, kich hoat lai ve DRAFT roi gui duyet lai\./i,
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Ly do tam ngung:/i)).not.toBeInTheDocument();
  });
});
