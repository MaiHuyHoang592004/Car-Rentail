import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), refresh: vi.fn(), push: vi.fn() }),
  usePathname: () => "/listings",
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { ListingsPageView } from "./listings-page-view";

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AuthProvider initialSession={null}>
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
      id: "lst-1",
      title: "Toyota Vios 2022",
      city: "Hanoi",
      category: "SEDAN",
      basePricePerDay: 700000,
      currency: "VND",
      seats: 5,
      transmission: "AUTO",
      fuelType: "GASOLINE",
      coverPhotoUrl: null,
      ratingAverage: null,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe("ListingsPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders listings from backend search API", async () => {
    fetchSpy.mockResolvedValue(jsonResponse(samplePage));

    wrap(<ListingsPageView />);

    await waitFor(() => expect(screen.getByText("Toyota Vios 2022")).toBeInTheDocument());
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/v1/listings?page=0&size=20");
  });

  it("re-queries with filter params when filters change", async () => {
    fetchSpy.mockResolvedValue(jsonResponse({ ...samplePage, content: [] }));
    wrap(<ListingsPageView />);

    await waitFor(() => expect(fetchSpy).toHaveBeenCalled());
    await userEvent.type(screen.getByPlaceholderText("Thành phố"), "Hanoi");
    await userEvent.selectOptions(screen.getByDisplayValue("Tất cả phân loại"), "SUV");

    await waitFor(() => {
      const call = fetchSpy.mock.calls.find(
        ([url]) =>
          typeof url === "string" &&
          url.startsWith("/api/v1/listings?") &&
          url.includes("city=Hanoi") &&
          url.includes("categories=SUV"),
      );
      expect(call).toBeDefined();
    });
  });

  it("does not call search API for invalid date range", async () => {
    fetchSpy.mockResolvedValue(jsonResponse(samplePage));
    const { container } = wrap(<ListingsPageView />);

    await waitFor(() => expect(screen.getByText("Toyota Vios 2022")).toBeInTheDocument());
    const dateInputs = container.querySelectorAll<HTMLInputElement>('input[type="date"]');
    await userEvent.type(dateInputs[0], "2026-07-03");
    await userEvent.type(dateInputs[1], "2026-07-01");

    expect(await screen.findByText("Ngày trả xe phải sau ngày nhận xe.")).toBeInTheDocument();
    const invalidDateCall = fetchSpy.mock.calls.find(
      ([url]) =>
        typeof url === "string" &&
        url.includes("pickupDate=2026-07-03") &&
        url.includes("returnDate=2026-07-01"),
    );
    expect(invalidDateCall).toBeUndefined();
  });
});
