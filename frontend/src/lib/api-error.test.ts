import { describe, expect, it } from "vitest";
import { ApiError, isApiError } from "./api-error";

describe("ApiError", () => {
  it("uses payload message when present", () => {
    const err = new ApiError(400, { code: "VALIDATION_ERROR", message: "bad input" });
    expect(err.message).toBe("bad input");
    expect(err.code).toBe("VALIDATION_ERROR");
    expect(err.status).toBe(400);
    expect(err.details).toEqual([]);
  });

  it("falls back to status when message and code are missing", () => {
    const err = new ApiError(500, { code: "", message: "" });
    expect(err.message).toContain("500");
    expect(err.code).toBe("UNKNOWN_ERROR");
  });

  it("exposes fieldError lookup", () => {
    const err = new ApiError(422, {
      code: "VALIDATION_ERROR",
      message: "bad",
      details: [{ field: "email", message: "required" }],
    });
    expect(err.fieldError("email")).toBe("required");
    expect(err.fieldError("missing")).toBeUndefined();
  });

  it("ApiError.network produces status 0 with NETWORK_ERROR code", () => {
    const err = ApiError.network("offline");
    expect(err.status).toBe(0);
    expect(err.code).toBe("NETWORK_ERROR");
    expect(err.message).toBe("offline");
  });

  it("isApiError narrows unknown to ApiError", () => {
    expect(isApiError(new ApiError(401, { code: "X", message: "y" }))).toBe(true);
    expect(isApiError(new Error("plain"))).toBe(false);
    expect(isApiError(null)).toBe(false);
  });
});
