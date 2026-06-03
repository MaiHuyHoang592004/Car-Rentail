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

describe("POST /api/v1/users/me/resend-verification", () => {
  beforeEach(() => {
    cookieValue = "REFRESH";
    mockedCallBackend.mockReset();
  });

  afterEach(() => vi.clearAllMocks());

  it("returns 401 without refresh cookie and does not call backend", async () => {
    cookieValue = undefined;

    const res = await POST();

    expect(res.status).toBe(401);
    expect(mockedCallBackend).not.toHaveBeenCalled();
  });

  it("refreshes session and forwards resend with backend Authorization header", async () => {
    mockedCallBackend
      .mockResolvedValueOnce(
        jsonResponse({
          tokenType: "Bearer",
          accessToken: "NEW_ACCESS",
          accessTokenExpiresAt: "2099-01-01T00:00:00Z",
          refreshToken: "NEW_REFRESH",
          refreshTokenExpiresAt: "2099-02-01T00:00:00Z",
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          id: "u-1",
          roles: ["CUSTOMER"],
        }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    const res = await POST();

    expect(res.status).toBe(204);
    expect(mockedCallBackend).toHaveBeenNthCalledWith(1, "/auth/refresh", {
      method: "POST",
      body: { refreshToken: "REFRESH" },
    });
    expect(mockedCallBackend).toHaveBeenNthCalledWith(2, "/users/me", {
      method: "GET",
      headers: { Authorization: "Bearer NEW_ACCESS" },
    });
    expect(mockedCallBackend).toHaveBeenNthCalledWith(3, "/users/me/resend-verification", {
      method: "POST",
      headers: { Authorization: "Bearer NEW_ACCESS" },
    });
    const cookies = res.headers.get("set-cookie") ?? "";
    expect(cookies).toContain("__Host-rentflow_refresh=NEW_REFRESH");
    expect(cookies).toContain("rentflow_role=CUSTOMER");
  });

  it("forwards backend auth failure with friendly code payload", async () => {
    mockedCallBackend
      .mockResolvedValueOnce(
        jsonResponse({
          tokenType: "Bearer",
          accessToken: "NEW_ACCESS",
          accessTokenExpiresAt: "2099-01-01T00:00:00Z",
          refreshToken: "NEW_REFRESH",
          refreshTokenExpiresAt: "2099-02-01T00:00:00Z",
        }),
      )
      .mockResolvedValueOnce(jsonResponse({ roles: ["CUSTOMER"] }))
      .mockResolvedValueOnce(
        jsonResponse(
          {
            code: "ACCESS_DENIED",
            message: "Access denied",
          },
          403,
        ),
      );

    const res = await POST();

    expect(res.status).toBe(403);
    await expect(res.json()).resolves.toMatchObject({ code: "ACCESS_DENIED" });
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
