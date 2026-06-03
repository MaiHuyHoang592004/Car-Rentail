import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

let cookieValue: string | undefined = "REFRESH";
vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) =>
      name === "__Host-rentflow_refresh" && cookieValue !== undefined
        ? { name, value: cookieValue }
        : undefined,
  }),
}));

vi.mock("@/lib/server/backend", () => ({
  callBackend: vi.fn(),
  forwardBackendError: vi.fn(async (response: Response) => {
    const { NextResponse } = await import("next/server");
    const payload = await response.json().catch(() => ({ code: "X", message: "y" }));
    return NextResponse.json(payload, { status: response.status });
  }),
}));

import { callBackend } from "@/lib/server/backend";
import { POST } from "./route";

const mockedCallBackend = vi.mocked(callBackend);

describe("POST /api/auth/refresh", () => {
  beforeEach(() => {
    cookieValue = "REFRESH";
    mockedCallBackend.mockReset();
  });
  afterEach(() => vi.clearAllMocks());

  it("returns 401 when no refresh cookie", async () => {
    cookieValue = undefined;
    const res = await POST();
    expect(res.status).toBe(401);
    expect(mockedCallBackend).not.toHaveBeenCalled();
  });

  it("on success returns new access token and rotates refresh cookie", async () => {
    mockedCallBackend
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            tokenType: "Bearer",
            accessToken: "NEW_ACCESS",
            accessTokenExpiresAt: "2099-01-01T00:00:00Z",
            refreshToken: "NEW_REFRESH",
            refreshTokenExpiresAt: "2099-02-01T00:00:00Z",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            id: "user-1",
            email: "user@example.com",
            emailVerified: false,
            roles: ["HOST"],
            fullName: "Host User",
            phone: null,
            dateOfBirth: null,
            addressLine: null,
            driverVerificationStatus: "NOT_SUBMITTED",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      );
    const res = await POST();
    expect(res.status).toBe(200);
    const body = (await res.json()) as { accessToken: string; refreshToken?: string };
    expect(body.accessToken).toBe("NEW_ACCESS");
    expect(body.refreshToken).toBeUndefined();
    expect(mockedCallBackend).toHaveBeenNthCalledWith(1, "/auth/refresh", {
      method: "POST",
      body: { refreshToken: "REFRESH" },
    });
    expect(mockedCallBackend).toHaveBeenNthCalledWith(2, "/users/me", {
      method: "GET",
      headers: {
        Authorization: "Bearer NEW_ACCESS",
      },
    });
    const cookies = res.headers.get("set-cookie") ?? "";
    expect(cookies).toContain("__Host-rentflow_refresh=NEW_REFRESH");
    expect(cookies).toContain("rentflow_role=HOST");
  });

  it("on backend failure clears refresh cookie and forwards status", async () => {
    mockedCallBackend.mockResolvedValue(
      new Response(JSON.stringify({ code: "AUTH_EXPIRED", message: "bad" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      }),
    );
    const res = await POST();
    expect(res.status).toBe(401);
    expect(res.headers.get("set-cookie") ?? "").toContain("Max-Age=0");
  });

  it("clears cookies and returns 204 when profile lookup fails after refresh", async () => {
    mockedCallBackend
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            tokenType: "Bearer",
            accessToken: "NEW_ACCESS",
            accessTokenExpiresAt: "2099-01-01T00:00:00Z",
            refreshToken: "NEW_REFRESH",
            refreshTokenExpiresAt: "2099-02-01T00:00:00Z",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      )
      .mockResolvedValueOnce(new Response(null, { status: 403 }));

    const res = await POST();
    expect(res.status).toBe(204);
    const cookies = res.headers.get("set-cookie") ?? "";
    expect(cookies).toContain("__Host-rentflow_refresh=");
    expect(cookies).toContain("rentflow_role=");
    expect(cookies).toContain("Max-Age=0");
  });
});
