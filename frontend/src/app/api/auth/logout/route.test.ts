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
}));

import { callBackend } from "@/lib/server/backend";
import { POST } from "./route";

const mockedCallBackend = vi.mocked(callBackend);

describe("POST /api/auth/logout", () => {
  beforeEach(() => {
    cookieValue = "REFRESH";
    mockedCallBackend.mockReset();
    mockedCallBackend.mockResolvedValue(new Response(null, { status: 204 }));
  });

  afterEach(() => vi.clearAllMocks());

  it("returns 204 and clears refresh cookie", async () => {
    const res = await POST();
    expect(res.status).toBe(204);
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain("__Host-rentflow_refresh=;");
    expect(sc).toContain("Max-Age=0");
  });

  it("calls backend /auth/logout with the refresh token", async () => {
    await POST();
    expect(mockedCallBackend).toHaveBeenCalledWith("/auth/logout", {
      method: "POST",
      body: { refreshToken: "REFRESH" },
    });
  });

  it("still returns 204 with cleared cookie when no cookie present", async () => {
    cookieValue = undefined;
    const res = await POST();
    expect(res.status).toBe(204);
    expect(mockedCallBackend).not.toHaveBeenCalled();
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain("Max-Age=0");
  });

  it("swallows backend errors but still returns 204", async () => {
    mockedCallBackend.mockRejectedValue(new Error("boom"));
    const res = await POST();
    expect(res.status).toBe(204);
  });
});
