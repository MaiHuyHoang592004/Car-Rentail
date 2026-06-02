import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  searchParams: "token=tok-123",
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/verify-email",
  useSearchParams: () => new URLSearchParams(mocks.searchParams),
}));

vi.mock("@/features/auth/auth-context", () => ({
  useAuth: () => ({
    status: "guest",
    user: null,
    roles: [],
    hasRole: () => false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refresh: vi.fn(),
  }),
}));

import { createApiClient, resetApiClient, setActiveApiClient } from "@/lib/api-client";
import { VerifyEmailPageView } from "./verify-email-page-view";

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>);
}

describe("VerifyEmailPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mocks.searchParams = "token=tok-123";
    resetApiClient();
    const apiClient = createApiClient();
    apiClient.setAccessTokenGetter(() => "STALE_ACCESS");
    apiClient.setRefreshHandler(vi.fn(async () => true));
    setActiveApiClient(apiClient);
    fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("verifies email without requiring a user session", async () => {
    wrap(<VerifyEmailPageView />);

    await screen.findByText("Xác minh thành công");
    await waitFor(() => expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/auth/verify-email",
      expect.objectContaining({ method: "POST" }),
    ));
    expect(fetchSpy).not.toHaveBeenCalledWith(
      "/api/v1/users/me",
      expect.anything(),
    );
    expect(
      fetchSpy.mock.calls.filter(([url]) => url === "/api/v1/auth/verify-email"),
    ).toHaveLength(1);
    const verifyCall = fetchSpy.mock.calls.find(([url]) => url === "/api/v1/auth/verify-email");
    expect(verifyCall).toBeTruthy();
    const verifyInit = verifyCall?.[1] as RequestInit;
    expect(new Headers(verifyInit.headers).has("Authorization")).toBe(false);
    expect(new Headers(verifyInit.headers).get("Content-Type")).toBe("application/json");
    expect(new Headers(verifyInit.headers).get("Accept")).toBe("application/json");
    expect(verifyInit.credentials).toBe("omit");
    expect(verifyInit.body).toBe(JSON.stringify({ token: "tok-123" }));
    expect(screen.getAllByRole("link", { name: "Đăng nhập" }).some((link) => link.getAttribute("href") === "/login")).toBe(true);
  });

  it("maps backend INVALID_TOKEN to friendly copy", async () => {
    fetchSpy.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          code: "INVALID_TOKEN",
          message: "Verification token is invalid or expired",
        }),
        { status: 409, headers: { "Content-Type": "application/json" } },
      ),
    );

    wrap(<VerifyEmailPageView />);

    expect(await screen.findByText(/Liên kết xác minh đã hết hạn hoặc không hợp lệ/)).toBeInTheDocument();
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/v1/auth/verify-email");
    expect(fetchSpy.mock.calls.some(([url]) => url === "/api/auth/refresh")).toBe(false);
  });

  it("does not call verify endpoint when token is missing", async () => {
    mocks.searchParams = "";

    wrap(<VerifyEmailPageView />);

    expect(await screen.findByText("Liên kết xác minh không hợp lệ hoặc thiếu token.")).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("runs the public verify request once across rerenders", async () => {
    const { rerender } = wrap(<VerifyEmailPageView />);

    await screen.findByText("Xác minh thành công");
    rerender(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <VerifyEmailPageView />
      </QueryClientProvider>,
    );

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1));
  });
});
