import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  usePathname: () => "/onboarding/customer/email",
}));

let authStatus: "loading" | "authenticated" | "guest" = "authenticated";
vi.mock("@/features/auth/auth-context", () => ({
  useAuth: () => ({
    status: authStatus,
    user: null,
    roles: authStatus === "authenticated" ? ["CUSTOMER"] : [],
    hasRole: (role: string) => authStatus === "authenticated" && role === "CUSTOMER",
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    logoutAll: vi.fn(),
    refresh: vi.fn(),
  }),
}));

const toastError = vi.fn();
vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: (message: string) => toastError(message),
  },
}));

import { createApiClient, resetApiClient, setActiveApiClient } from "@/lib/api-client";
import { EmailOnboardingPageView } from "./email-onboarding-page-view";

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>);
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("EmailOnboardingPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    authStatus = "authenticated";
    const apiClient = createApiClient();
    apiClient.setAccessTokenGetter(() => "ACCESS");
    setActiveApiClient(apiClient);
    toastError.mockClear();
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it("does not call profile endpoint while auth status is loading", () => {
    authStatus = "loading";

    wrap(<EmailOnboardingPageView />);

    expect(screen.getByText("Đang tải trạng thái email...")).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("shows login CTA for guest session and does not call protected APIs", () => {
    authStatus = "guest";

    wrap(<EmailOnboardingPageView />);

    expect(screen.getByText("Phiên đăng nhập không còn hợp lệ")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đăng nhập lại" })).toHaveAttribute(
      "href",
      "/login?next=%2Fonboarding%2Fcustomer%2Femail",
    );
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("maps EMAIL_DELIVERY_FAILED resend failure to friendly copy", async () => {
    fetchSpy
      .mockResolvedValueOnce(
        jsonResponse({
          id: "u-1",
          email: "u@example.com",
          emailVerified: false,
          roles: ["CUSTOMER"],
          fullName: "User",
          phone: "",
          dateOfBirth: null,
          addressLine: "",
          driverVerificationStatus: "NOT_SUBMITTED",
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          {
            code: "EMAIL_DELIVERY_FAILED",
            message: "Verification email could not be sent",
          },
          409,
        ),
      );

    wrap(<EmailOnboardingPageView />);

    await screen.findByText("Kiểm tra hộp thư của bạn");
    await userEvent.click(screen.getByRole("button", { name: "Gửi lại email xác minh" }));

    expect(await screen.findByText("Chưa gửi được email xác minh. Vui lòng thử lại sau.")).toBeInTheDocument();
    expect(toastError).toHaveBeenCalledWith("Chưa gửi được email xác minh. Vui lòng thử lại sau.");
  });

  it("sends resend request with active access token and maps 403 to session copy", async () => {
    fetchSpy
      .mockResolvedValueOnce(
        jsonResponse({
          id: "u-1",
          email: "u@example.com",
          emailVerified: false,
          roles: ["CUSTOMER"],
          fullName: "User",
          phone: "",
          dateOfBirth: null,
          addressLine: "",
          driverVerificationStatus: "NOT_SUBMITTED",
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          {
            code: "ACCESS_DENIED",
            message: "Access denied",
          },
          403,
        ),
      );

    wrap(<EmailOnboardingPageView />);

    await screen.findByText("Kiểm tra hộp thư của bạn");
    await userEvent.click(screen.getByRole("button", { name: "Gửi lại email xác minh" }));

    const resendCall = fetchSpy.mock.calls[1] as [string, RequestInit];
    expect(resendCall[0]).toBe("/api/v1/users/me/resend-verification");
    expect(new Headers(resendCall[1].headers).get("Authorization")).toBe("Bearer ACCESS");
    expect(await screen.findByText("Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.")).toBeInTheDocument();
  });
});
