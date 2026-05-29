import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), refresh: vi.fn(), push: vi.fn() }),
  usePathname: () => "/me/bookings",
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { BookingsListPageView } from "./bookings-list-page-view";

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

const samplePage = {
  content: [
    {
      id: "bk-1",
      status: "HELD",
      listingId: "lst-1",
      listingTitle: "Toyota Vios 2022",
      pickupDate: "2026-06-18",
      returnDate: "2026-06-21",
      holdExpiresAt: null,
      totalAmount: 2280000,
      currency: "VND",
      voidRetryRequired: false,
      paymentRetryState: null,
      createdAt: "2026-06-01T00:00:00Z",
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe("BookingsListPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders bookings from /bookings/me without status filter when ALL", async () => {
    fetchSpy.mockResolvedValue(jsonResponse(samplePage));
    wrap(<BookingsListPageView />);
    await waitFor(() => expect(screen.getByText("Toyota Vios 2022")).toBeInTheDocument());
    const bookingCalls = fetchSpy.mock.calls.filter(
      ([url]) => typeof url === "string" && url.startsWith("/api/v1/bookings/me"),
    );
    expect(bookingCalls).toHaveLength(1);
    const url = bookingCalls[0][0] as string;
    expect(url).not.toContain("status=");
  });

  it("re-queries with status param when filter is changed", async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse({ ...samplePage, content: [], totalElements: 0, totalPages: 0 }),
    );
    wrap(<BookingsListPageView />);
    await waitFor(() => {
      const calls = fetchSpy.mock.calls.filter(([u]) =>
        typeof u === "string" && u.startsWith("/api/v1/bookings/me"),
      );
      expect(calls.length).toBeGreaterThan(0);
    });
    const filterButtons = screen.getAllByRole("button", { name: "HELD" });
    await userEvent.click(filterButtons[0]);
    await waitFor(() => {
      const heldCall = fetchSpy.mock.calls.find(
        ([u]) => typeof u === "string" && u.startsWith("/api/v1/bookings/me") && u.includes("status=HELD"),
      );
      expect(heldCall).toBeDefined();
    });
  });

  it("shows empty state when no rows", async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    );
    const { container } = wrap(<BookingsListPageView />);
    await waitFor(() => {
      expect(within(container).getByText("Chua co don nao")).toBeInTheDocument();
    });
  });

  it("shows retry-state marker for cancelled bookings pending payment cleanup", async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse({
        ...samplePage,
        content: [
          {
            ...samplePage.content[0],
            status: "CANCELLED",
            voidRetryRequired: true,
            paymentRetryState: "VOID_RETRY_REQUIRED",
          },
        ],
      }),
    );
    wrap(<BookingsListPageView />);

      expect(
        await screen.findByText("Dang xu ly hoan tien hoac void trong nen"),
      ).toBeInTheDocument();
  });

  it("does not show retry-state marker for ordinary cancelled bookings", async () => {
    fetchSpy.mockResolvedValue(
      jsonResponse({
        ...samplePage,
        content: [
          {
            ...samplePage.content[0],
            status: "CANCELLED",
            voidRetryRequired: false,
            paymentRetryState: null,
          },
        ],
      }),
    );
    wrap(<BookingsListPageView />);
    await screen.findByText("Toyota Vios 2022");
    expect(screen.queryByText("Dang xu ly hoan tien hoac void trong nen")).not.toBeInTheDocument();
  });
});
