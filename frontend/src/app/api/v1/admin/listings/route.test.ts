import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";

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
}));

import { callBackend } from "@/lib/server/backend";
import { GET } from "./route";

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

describe("GET /api/v1/admin/listings", () => {
  beforeEach(() => {
    cookieValue = "REFRESH";
    mockedCallBackend.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("refreshes session and forwards list query to the backend", async () => {
    mockedCallBackend
      .mockResolvedValueOnce(
        jsonResponse({
          accessToken: "NEW_ACCESS",
          refreshToken: "NEW_REFRESH",
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          roles: ["CUSTOMER", "ADMIN"],
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        }),
      );

    const request = new NextRequest(
      "http://localhost/api/v1/admin/listings?status=PENDING_APPROVAL&page=0&size=20",
      {
        method: "GET",
        headers: {
          Accept: "application/json",
          "X-Correlation-Id": "corr-123",
        },
      },
    );

    const response = await GET(request);

    expect(response.status).toBe(200);
    expect(mockedCallBackend).toHaveBeenNthCalledWith(
      3,
      "/admin/listings?status=PENDING_APPROVAL&page=0&size=20",
      expect.objectContaining({
        method: "GET",
      }),
    );
    const forwarded = mockedCallBackend.mock.calls[2]?.[1] as {
      headers: Headers;
    };
    expect(forwarded.headers.get("Authorization")).toBe("Bearer NEW_ACCESS");
    expect(forwarded.headers.get("X-Correlation-Id")).toBe("corr-123");
  });

  it("forwards backend authorization failures", async () => {
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
            code: "ACCESS_DENIED",
            message: "Access denied",
          },
          403,
        ),
      );

    const request = new NextRequest("http://localhost/api/v1/admin/listings?page=0&size=1", {
      method: "GET",
    });

    const response = await GET(request);

    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toMatchObject({
      code: "ACCESS_DENIED",
    });
  });
});
