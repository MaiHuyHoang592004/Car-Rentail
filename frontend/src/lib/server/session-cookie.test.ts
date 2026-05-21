import { describe, expect, it, vi } from "vitest";

vi.stubEnv("NODE_ENV", "test");

const { NextResponse } = await import("next/server");
const {
  REFRESH_COOKIE_NAME,
  clearRefreshCookie,
  setRefreshCookie,
} = await import("./session-cookie");

describe("session-cookie helpers", () => {
  it("setRefreshCookie sets httpOnly cookie with 7-day maxAge and sameSite=lax", () => {
    const res = NextResponse.json({});
    setRefreshCookie(res, "refresh-token-xyz");
    const cookie = res.cookies.get(REFRESH_COOKIE_NAME);
    expect(cookie?.value).toBe("refresh-token-xyz");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain("HttpOnly");
    expect(sc.toLowerCase()).toContain("samesite=lax");
    expect(sc).toContain("Path=/");
    expect(sc).toContain("Max-Age=604800");
  });

  it("clearRefreshCookie sets maxAge=0 to expire", () => {
    const res = NextResponse.json({});
    clearRefreshCookie(res);
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain("Max-Age=0");
    expect(sc).toContain("HttpOnly");
  });

  it("does not set Secure flag in non-production env", () => {
    const res = NextResponse.json({});
    setRefreshCookie(res, "token");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc.toLowerCase()).not.toContain("secure");
  });
});
