import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), refresh: vi.fn(), push: vi.fn() }),
  usePathname: () => "/me/profile",
}));

const toastSuccess = vi.fn();
const toastError = vi.fn();
vi.mock("sonner", () => ({
  toast: {
    success: (msg: string) => toastSuccess(msg),
    error: (msg: string) => toastError(msg),
  },
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { ProfilePageView } from "./profile-page-view";

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

describe("ProfilePageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    toastSuccess.mockClear();
    toastError.mockClear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows resend verification CTA for unverified email and posts to resend endpoint", async () => {
    fetchSpy
      .mockResolvedValueOnce(
        jsonResponse({
          id: "u-1",
          email: "u@e.com",
          emailVerified: false,
          roles: ["CUSTOMER"],
          fullName: "User",
          phone: "",
          dateOfBirth: null,
          addressLine: "",
          driverVerificationStatus: "NOT_SUBMITTED",
        }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    wrap(<ProfilePageView />);

    await screen.findByText("Email của bạn chưa được xác minh");
    await userEvent.click(screen.getByRole("button", { name: "Gửi lại email xác minh" }));

    await waitFor(() =>
      expect(toastSuccess).toHaveBeenCalledWith(
        "Đã gửi lại email xác minh. Vui lòng kiểm tra hộp thư của bạn.",
      ),
    );
    expect(fetchSpy.mock.calls[1]?.[0]).toBe("/api/v1/users/me/resend-verification");
  });
});
