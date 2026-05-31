import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { routerPush, toastSuccess, toastError } = vi.hoisted(() => ({
  routerPush: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: routerPush }),
  usePathname: () => "/host/vehicles/new",
}));

vi.mock("sonner", () => ({
  toast: {
    success: toastSuccess,
    error: toastError,
  },
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { HostVehicleCreatePageView } from "./host-vehicle-create-page-view";

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

describe("HostVehicleCreatePageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: vi.fn(() => "blob:vehicle-photo"),
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: vi.fn(),
    });
    routerPush.mockClear();
    toastSuccess.mockClear();
    toastError.mockClear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("creates vehicle via API and redirects to requested status tab", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse(
        {
          id: "vh-001",
          category: "SUV",
          make: "Toyota",
          model: "Fortuner",
          year: 2024,
          transmission: "AUTO",
          fuelType: "PETROL",
          seats: 7,
          status: "ACTIVE",
          city: "Ho Chi Minh",
          plateNumber: "51H-888.88",
          vin: "VIN123456789",
        },
        201,
      ),
    );

    const user = userEvent.setup();
    const { container } = wrap(<HostVehicleCreatePageView />);

    const selects = container.querySelectorAll("select");
    await user.selectOptions(selects[0]!, "Toyota");
    await user.selectOptions(selects[1]!, "Fortuner");
    await user.selectOptions(selects[2]!, "2024");
    await user.selectOptions(selects[3]!, "Ho Chi Minh");

    const inputs = container.querySelectorAll<HTMLInputElement>('input[type="text"]');
    await user.type(inputs[0]!, "51H-888.88");
    await user.type(inputs[1]!, "VIN123456789");

    await user.click(screen.getByRole("button", { name: /SUV/i }));
    await user.click(screen.getByRole("button", { name: /Xang$/i }));
    await user.click(screen.getByRole("button", { name: /7 cho/i }));
    await user.click(screen.getByRole("button", { name: /Kich hoat/i }));
    await user.click(screen.getByRole("button", { name: /Tao va kich hoat/i }));

    await waitFor(() => expect(routerPush).toHaveBeenCalledWith("/host/vehicles?status=ACTIVE"));

    const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/host/vehicles");
    expect(init.method).toBe("POST");
    expect(JSON.parse(String(init.body))).toEqual({
      category: "SUV",
      make: "Toyota",
      model: "Fortuner",
      year: 2024,
      plateNumber: "51H-888.88",
      vin: "VIN123456789",
      transmission: "AUTO",
      fuelType: "PETROL",
      seats: 7,
      city: "Ho Chi Minh",
      status: "ACTIVE",
    });
    expect(toastSuccess).toHaveBeenCalledWith("Da tao xe thanh cong.");
  }, 10000);

  it("uploads selected vehicle photos after vehicle creation", async () => {
    fetchSpy
      .mockResolvedValueOnce(
        jsonResponse(
          {
            id: "vh-photos",
            category: "SUV",
            make: "Toyota",
            model: "Fortuner",
            year: 2024,
            transmission: "AUTO",
            fuelType: "PETROL",
            seats: 7,
            status: "DRAFT",
            city: "Ho Chi Minh",
            plateNumber: "51H-888.88",
            vin: "VIN123456789",
          },
          201,
        ),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          {
            fileId: "file-1",
            bucket: "rentflow-vehicle-photos",
            objectKey: "vehicles/vh-photos/file-1",
            uploadUrl: "https://upload.local/file-1",
            expiresAt: "2026-05-29T00:10:00Z",
          },
          200,
        ),
      )
      .mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(
        jsonResponse(
          {
            fileId: "file-1",
            visibility: "PRIVATE",
            signedUrl: "https://files.local/photo-1",
            signedUrlExpiresAt: "2026-05-29T00:10:00Z",
          },
          200,
        ),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          {
            id: "photo-1",
            vehicleId: "vh-photos",
            fileId: "file-1",
            primary: true,
            displayOrder: 0,
            visibility: "PRIVATE",
            signedUrl: "https://files.local/photo-1",
            signedUrlExpiresAt: "2026-05-29T00:10:00Z",
          },
          201,
        ),
      );

    const user = userEvent.setup();
    const { container } = wrap(<HostVehicleCreatePageView />);

    await user.upload(
      screen.getByLabelText(/Chọn ảnh xe/i),
      new File(["photo"], "front-view.jpg", { type: "image/jpeg" }),
    );

    const selects = container.querySelectorAll("select");
    await user.selectOptions(selects[0]!, "Toyota");
    await user.selectOptions(selects[1]!, "Fortuner");
    await user.selectOptions(selects[2]!, "2024");
    await user.selectOptions(selects[3]!, "Ho Chi Minh");

    const inputs = container.querySelectorAll<HTMLInputElement>('input[type="text"]');
    await user.type(inputs[0]!, "51H-888.88");
    await user.type(inputs[1]!, "VIN123456789");

    await user.click(screen.getByRole("button", { name: /SUV/i }));
    await user.click(screen.getByRole("button", { name: /Xang$/i }));
    await user.click(screen.getByRole("button", { name: /7 cho/i }));
    await user.click(screen.getByRole("button", { name: /Luu nhap/i }));
    await user.click(screen.getByRole("button", { name: /Luu vao Draft/i }));

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(5));

    const [intentUrl, intentInit] = fetchSpy.mock.calls[1] as [string, RequestInit];
    expect(intentUrl).toBe("/api/v1/host/vehicles/vh-photos/photos/upload-intents");
    expect(intentInit.method).toBe("POST");
    expect(JSON.parse(String(intentInit.body))).toMatchObject({
      contentType: "image/jpeg",
      sizeBytes: 5,
    });

    const [uploadUrl, uploadInit] = fetchSpy.mock.calls[2] as [string, RequestInit];
    expect(uploadUrl).toBe("https://upload.local/file-1");
    expect(uploadInit.method).toBe("PUT");

    const [finalizeUrl, finalizeInit] = fetchSpy.mock.calls[3] as [string, RequestInit];
    expect(finalizeUrl).toBe("/api/v1/files/file-1/finalize");
    expect(finalizeInit.method).toBe("POST");

    const [photoUrl, photoInit] = fetchSpy.mock.calls[4] as [string, RequestInit];
    expect(photoUrl).toBe("/api/v1/host/vehicles/vh-photos/photos");
    expect(photoInit.method).toBe("POST");
    expect(JSON.parse(String(photoInit.body))).toMatchObject({
      fileId: "file-1",
      primary: true,
    });
    expect(routerPush).toHaveBeenCalledWith("/host/vehicles?status=DRAFT");
  }, 10000);

  it("keeps vehicle creation successful when binary photo upload fails", async () => {
    fetchSpy
      .mockResolvedValueOnce(
        jsonResponse(
          {
            id: "vh-upload-fail",
            category: "SUV",
            make: "Toyota",
            model: "Fortuner",
            year: 2024,
            transmission: "AUTO",
            fuelType: "PETROL",
            seats: 7,
            status: "DRAFT",
            city: "Ho Chi Minh",
            plateNumber: "51H-888.88",
            vin: "VIN123456789",
          },
          201,
        ),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          {
            fileId: "file-fail",
            bucket: "rentflow-vehicle-photos",
            objectKey: "vehicles/vh-upload-fail/file-fail",
            uploadUrl: "https://upload.local/file-fail",
            expiresAt: "2026-05-29T00:10:00Z",
          },
          200,
        ),
      )
      .mockResolvedValueOnce(new Response(null, { status: 500 }));

    const user = userEvent.setup();
    const { container } = wrap(<HostVehicleCreatePageView />);

    await user.upload(
      screen.getByLabelText(/Chọn ảnh xe/i),
      new File(["photo"], "front-view.jpg", { type: "image/jpeg" }),
    );

    const selects = container.querySelectorAll("select");
    await user.selectOptions(selects[0]!, "Toyota");
    await user.selectOptions(selects[1]!, "Fortuner");
    await user.selectOptions(selects[2]!, "2024");
    await user.selectOptions(selects[3]!, "Ho Chi Minh");

    const inputs = container.querySelectorAll<HTMLInputElement>('input[type="text"]');
    await user.type(inputs[0]!, "51H-888.88");
    await user.type(inputs[1]!, "VIN123456789");

    await user.click(screen.getByRole("button", { name: /SUV/i }));
    await user.click(screen.getByRole("button", { name: /Xang$/i }));
    await user.click(screen.getByRole("button", { name: /7 cho/i }));
    await user.click(screen.getByRole("button", { name: /Luu nhap/i }));
    await user.click(screen.getByRole("button", { name: /Luu vao Draft/i }));

    await waitFor(() => expect(routerPush).toHaveBeenCalledWith("/host/vehicles/vh-upload-fail"));
    expect(toastError).toHaveBeenCalledWith(
      "Xe đã được tạo, một số ảnh tải lên thất bại. Vui lòng thử lại ở chi tiết xe.",
    );
  }, 10000);

  it("previews selected vehicle photos before submit", async () => {
    const user = userEvent.setup();
    wrap(<HostVehicleCreatePageView />);

    const file = new File(["photo"], "front-view.jpg", { type: "image/jpeg" });
    await user.upload(screen.getByLabelText(/Chọn ảnh xe/i), file);

    await waitFor(() => expect(screen.getAllByAltText("front-view.jpg")).toHaveLength(2));
    expect(screen.getByText("Ảnh bìa")).toBeInTheDocument();
  });
});
