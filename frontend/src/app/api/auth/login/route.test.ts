import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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

const tokenResponse = {
  tokenType: "Bearer",
  accessToken: "ACCESS",
  accessTokenExpiresAt: "2099-01-01T00:00:00Z",
  refreshToken: "REFRESH",
  refreshTokenExpiresAt: "2099-02-01T00:00:00Z",
  user: {
    id: "u-1",
    email: "u@e.com",
    roles: ["CUSTOMER"],
    fullName: "U",
    phone: null,
    dateOfBirth: null,
    addressLine: null,
    driverVerificationStatus: "NOT_SUBMITTED",
  },
};

function makeRequest(body: unknown) {
  return new Request("http://localhost/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

describe("POST /api/auth/login", () => {
  beforeEach(() => {
    mockedCallBackend.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("returns 400 when body is missing email/password", async () => {
    const res = await POST(makeRequest({ email: "u@e.com" }));
    expect(res.status).toBe(400);
    const json = (await res.json()) as { code: string };
    expect(json.code).toBe("VALIDATION_ERROR");
    expect(mockedCallBackend).not.toHaveBeenCalled();
  });

  it("returns 400 when body is not JSON", async () => {
    const req = new Request("http://localhost/api/auth/login", {
      method: "POST",
      body: "not-json",
    });
    const res = await POST(req);
    expect(res.status).toBe(400);
  });

  it("on success returns session payload (no refreshToken in body) and sets httpOnly cookie", async () => {
    mockedCallBackend.mockResolvedValue(
      new Response(JSON.stringify(tokenResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    const res = await POST(makeRequest({ email: "u@e.com", password: "pw" }));
    expect(res.status).toBe(200);
    const body = (await res.json()) as { accessToken: string; refreshToken?: string; user: { id: string } };
    expect(body.accessToken).toBe("ACCESS");
    expect(body.user.id).toBe("u-1");
    expect(body.refreshToken).toBeUndefined();

    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain("rentflow_refresh=REFRESH");
    expect(sc).toContain("HttpOnly");
    expect(sc.toLowerCase()).toContain("samesite=lax");
  });

  it("forwards backend error status and body on failure", async () => {
    mockedCallBackend.mockResolvedValue(
      new Response(
        JSON.stringify({ code: "AUTH_INVALID_CREDENTIALS", message: "wrong" }),
        { status: 401, headers: { "Content-Type": "application/json" } },
      ),
    );
    const res = await POST(makeRequest({ email: "u@e.com", password: "pw" }));
    expect(res.status).toBe(401);
    const json = (await res.json()) as { code: string };
    expect(json.code).toBe("AUTH_INVALID_CREDENTIALS");
  });

  it("calls backend with /auth/login path and email/password body only", async () => {
    mockedCallBackend.mockResolvedValue(
      new Response(JSON.stringify(tokenResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    await POST(makeRequest({ email: "u@e.com", password: "pw", extra: "ignored" }));
    expect(mockedCallBackend).toHaveBeenCalledWith("/auth/login", {
      method: "POST",
      body: { email: "u@e.com", password: "pw" },
    });
  });
});
