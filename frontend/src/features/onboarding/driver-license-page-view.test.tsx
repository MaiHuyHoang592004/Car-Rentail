import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const routerReplace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: routerReplace }),
  usePathname: () => "/onboarding/customer/driver-license",
}));

vi.mock("@/features/auth/auth-context", () => ({
  useAuth: () => ({
    status: "authenticated",
    user: null,
    roles: ["CUSTOMER"],
    hasRole: (role: string) => role === "CUSTOMER",
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refresh: vi.fn(),
  }),
}));

import { resetApiClient } from "@/lib/api-client";
import { DriverLicensePageView } from "./driver-license-page-view";

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>);
}

describe("DriverLicensePageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetApiClient();
    routerReplace.mockClear();
    fetchSpy = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: "dv-1", status: "PENDING" }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("labels documentFileId as demo-only and posts exact backend payload", async () => {
    wrap(<DriverLicensePageView />);

    expect(screen.getByLabelText("Mã tài liệu GPLX demo")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Ví dụ: 550e8400-e29b-41d4-a716-446655440000")).toBeInTheDocument();
    expect(screen.getByText(/UUID gồm 5 nhóm ký tự theo dạng 8-4-4-4-12/)).toBeInTheDocument();
    expect(screen.getByText(/Demo\/dev chấp nhận UUID đúng định dạng/)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText("Số GPLX"), "B1234567");
    await userEvent.type(screen.getByLabelText("Ngày hết hạn GPLX"), "2099-01-01");
    await userEvent.type(
      screen.getByLabelText("Mã tài liệu GPLX demo"),
      "11111111-1111-4111-8111-111111111111",
    );
    await userEvent.click(screen.getByRole("button", { name: "Gửi GPLX để duyệt" }));

    await waitFor(() =>
      expect(routerReplace).toHaveBeenCalledWith("/onboarding/customer/driver-license/pending"),
    );
    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/users/me/driver-license",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          licenseNumber: "B1234567",
          licenseExpiryDate: "2099-01-01",
          documentFileId: "11111111-1111-4111-8111-111111111111",
        }),
      }),
    );
  });

  it("maps driver-license endpoint 404 to a developer-friendly rewrite message", async () => {
    fetchSpy.mockResolvedValueOnce(
      new Response(JSON.stringify({ code: "REQUEST_FAILED", message: "Not Found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" },
      }),
    );
    wrap(<DriverLicensePageView />);

    await userEvent.type(screen.getByLabelText("Số GPLX"), "B1234567");
    await userEvent.type(screen.getByLabelText("Ngày hết hạn GPLX"), "2099-01-01");
    await userEvent.type(
      screen.getByLabelText("Mã tài liệu GPLX demo"),
      "11111111-1111-4111-8111-111111111111",
    );
    await userEvent.click(screen.getByRole("button", { name: "Gửi GPLX để duyệt" }));

    expect(
      await screen.findByText("Không tìm thấy endpoint xác minh GPLX. Kiểm tra API/rewrite."),
    ).toBeInTheDocument();
    expect(routerReplace).not.toHaveBeenCalled();
  });
});
