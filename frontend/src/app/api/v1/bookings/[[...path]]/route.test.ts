import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";

let cookieValue: string | undefined = "REFRESH";
vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) =>
      name === "rentflow_refresh" && cookieValue !== undefined
        ? { name, value: cookieValue }
        : undefined,
  }),
}));

vi.mock("@/lib/server/backend", () => ({
  callBackend: vi.fn(),
}));

import { callBackend } from "@/lib/server/backend";
import { GET, POST } from "./route";

const mockedCallBackend = vi.mocked(callBackend);

function jsonResponse(body: unknown, status = 200, headers?: HeadersInit): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...headers,
    },
  });
}

describe("booking BFF route", () => {
  beforeEach(() => {
    cookieValue = "REFRESH";
    mockedCallBackend.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("returns 401 when refresh cookie is missing", async () => {
    cookieValue = undefined;

    const request = new NextRequest("http://localhost/api/v1/bookings/me", {
      method: "GET",
    });
    const response = await GET(request, { params: Promise.resolve({ path: ["me"] }) });

    expect(response.status).toBe(401);
    expect(mockedCallBackend).not.toHaveBeenCalled();
  });

  it("clears auth cookies when refresh backend call fails", async () => {
    mockedCallBackend.mockResolvedValueOnce(
      jsonResponse(
        { code: "AUTH_EXPIRED", message: "expired" },
        401,
        { "Retry-After": "60" },
      ),
    );

    const request = new NextRequest("http://localhost/api/v1/bookings/me", {
      method: "GET",
    });
    const response = await GET(request, { params: Promise.resolve({ path: ["me"] }) });

    expect(response.status).toBe(401);
    expect(response.headers.get("retry-after")).toBe("60");
    const cookies = response.headers.get("set-cookie") ?? "";
    expect(cookies).toContain("rentflow_refresh=");
    expect(cookies).toContain("rentflow_role=");
    expect(cookies).toContain("Max-Age=0");
  });

  it("refreshes session, forwards request payload and rotates cookies", async () => {
    mockedCallBackend
      .mockResolvedValueOnce(
        jsonResponse({
          accessToken: "NEW_ACCESS",
          refreshToken: "NEW_REFRESH",
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          roles: ["CUSTOMER"],
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          {
            id: "bk-1",
            status: "CANCELLED",
            cancellationCompleted: true,
          },
          202,
        ),
      );

    const request = new NextRequest("http://localhost/api/v1/bookings/bk-1/cancel?source=detail", {
      method: "POST",
      headers: {
        Authorization: "Bearer BROWSER_TOKEN",
        "Content-Type": "application/json",
        Accept: "application/json",
        "Idempotency-Key": "idem-123",
        "X-Correlation-Id": "corr-123",
      },
      body: JSON.stringify({ reason: "test" }),
    });

    const response = await POST(request, {
      params: Promise.resolve({ path: ["bk-1", "cancel"] }),
    });

    expect(response.status).toBe(202);
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
    expect(mockedCallBackend).toHaveBeenNthCalledWith(
      3,
      "/bookings/bk-1/cancel?source=detail",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ reason: "test" }),
      }),
    );
    const thirdCall = mockedCallBackend.mock.calls[2]?.[1] as {
      headers: Headers;
    };
    expect(thirdCall.headers.get("Authorization")).toBe("Bearer NEW_ACCESS");
    expect(thirdCall.headers.get("Idempotency-Key")).toBe("idem-123");
    expect(thirdCall.headers.get("X-Correlation-Id")).toBe("corr-123");

    const cookies = response.headers.get("set-cookie") ?? "";
    expect(cookies).toContain("rentflow_refresh=NEW_REFRESH");
    expect(cookies).toContain("rentflow_role=CUSTOMER");
    await expect(response.json()).resolves.toMatchObject({
      id: "bk-1",
      cancellationCompleted: true,
    });
  });
});
