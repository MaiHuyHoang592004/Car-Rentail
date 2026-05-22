import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-error";
import { handleApiError } from "@/lib/handle-api-error";

vi.mock("sonner", () => ({
  toast: { error: vi.fn() },
}));

import { toast } from "sonner";

afterEach(() => {
  vi.clearAllMocks();
});

describe("handleApiError", () => {
  it("routes ApiError code to onCode handler", () => {
    const onCode = vi.fn();
    const err = new ApiError(409, { code: "BOOKING_OVERLAP_CUSTOMER", message: "overlap" });
    handleApiError(err, { onCode: { BOOKING_OVERLAP_CUSTOMER: onCode } });
    expect(onCode).toHaveBeenCalledWith(err);
  });

  it("onCode wins over onFieldError for VALIDATION_ERROR", () => {
    const onCode = vi.fn();
    const onFieldError = vi.fn();
    const err = new ApiError(400, {
      code: "VALIDATION_ERROR",
      message: "x",
      details: [{ field: "pickupDate", message: "bad" }],
    });
    handleApiError(err, { onCode: { VALIDATION_ERROR: onCode }, onFieldError });
    expect(onCode).toHaveBeenCalled();
    expect(onFieldError).not.toHaveBeenCalled();
  });

  it("calls onFieldError per detail for VALIDATION_ERROR", () => {
    const onFieldError = vi.fn();
    const err = new ApiError(400, {
      code: "VALIDATION_ERROR",
      message: "x",
      details: [
        { field: "pickupDate", message: "missing" },
        { field: "returnDate", message: "missing" },
      ],
    });
    handleApiError(err, { onFieldError });
    expect(onFieldError).toHaveBeenCalledTimes(2);
    expect(onFieldError).toHaveBeenNthCalledWith(1, "pickupDate", "missing");
    expect(onFieldError).toHaveBeenNthCalledWith(2, "returnDate", "missing");
  });

  it("falls through to onUnknown when VALIDATION_ERROR has no details", () => {
    const onFieldError = vi.fn();
    const onUnknown = vi.fn();
    const err = new ApiError(400, { code: "VALIDATION_ERROR", message: "bad" });
    handleApiError(err, { onFieldError, onUnknown });
    expect(onFieldError).not.toHaveBeenCalled();
    expect(onUnknown).toHaveBeenCalledWith(err);
  });

  it("calls onUnknown for ApiError not matched", () => {
    const onUnknown = vi.fn();
    const err = new ApiError(500, { code: "SERVER_ERROR", message: "boom" });
    handleApiError(err, { onUnknown });
    expect(onUnknown).toHaveBeenCalledWith(err);
  });

  it("calls onNetwork for non-ApiError", () => {
    const onNetwork = vi.fn();
    handleApiError(new TypeError("offline"), { onNetwork });
    expect(onNetwork).toHaveBeenCalled();
  });

  it("default onUnknown toasts err.message", () => {
    const err = new ApiError(500, { code: "X", message: "kaboom" });
    handleApiError(err, {});
    expect(toast.error).toHaveBeenCalledWith("kaboom");
  });

  it("default onNetwork toasts generic message", () => {
    handleApiError(new Error("nope"), {});
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining("Lỗi kết nối"));
  });
});
