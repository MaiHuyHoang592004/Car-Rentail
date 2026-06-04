import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.stubEnv("NODE_ENV", "test");
vi.stubEnv("COOKIE_SECURE", "");
vi.stubEnv("COOKIE_SAME_SITE", "");

const { NextResponse } = await import("next/server");
const { getRefreshCookieName } = await import("../session-cookie-shared");
const {
  REFRESH_COOKIE_NAME,
  ROLE_COOKIE_NAME,
  clearRefreshCookie,
  clearRoleCookie,
  setRefreshCookie,
  setRoleCookie,
} = await import("./session-cookie");

describe("session-cookie helpers", () => {
  beforeEach(() => {
    vi.stubEnv("NODE_ENV", "test");
    vi.stubEnv("COOKIE_SECURE", "");
    vi.stubEnv("COOKIE_SAME_SITE", "");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

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

  it("sets Secure flag by default outside development", () => {
    const res = NextResponse.json({});
    setRefreshCookie(res, "token");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain("Secure");
  });

  it("sets Secure flag when COOKIE_SECURE=true", () => {
    vi.stubEnv("COOKIE_SECURE", "true");
    const res = NextResponse.json({});
    setRefreshCookie(res, "token");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain("Secure");
    vi.stubEnv("COOKIE_SECURE", "");
  });

  it("omits Secure flag when COOKIE_SECURE=false", () => {
    vi.stubEnv("COOKIE_SECURE", "false");
    const res = NextResponse.json({});
    setRefreshCookie(res, "token");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain("rentflow_refresh=token");
    expect(sc.toLowerCase()).not.toContain("secure");
  });

  it("omits Secure flag in development when COOKIE_SECURE is unset", () => {
    vi.stubEnv("NODE_ENV", "development");
    const res = NextResponse.json({});
    setRefreshCookie(res, "token");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain("rentflow_refresh=token");
    expect(sc.toLowerCase()).not.toContain("secure");
  });

  it("keeps __Host- prefix only when secure cookies are enabled", () => {
    expect(REFRESH_COOKIE_NAME).toBe("__Host-rentflow_refresh");
    expect(getRefreshCookieName()).toBe("__Host-rentflow_refresh");

    vi.stubEnv("COOKIE_SECURE", "false");
    expect(getRefreshCookieName()).toBe("rentflow_refresh");

    vi.stubEnv("COOKIE_SECURE", "");
    vi.stubEnv("NODE_ENV", "development");
    expect(getRefreshCookieName()).toBe("rentflow_refresh");
  });

  it("uses configured SameSite policy when valid", () => {
    vi.stubEnv("COOKIE_SAME_SITE", "strict");
    const res = NextResponse.json({});
    setRefreshCookie(res, "token");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc.toLowerCase()).toContain("samesite=strict");
  });

  it("falls back to SameSite=lax for invalid values", () => {
    vi.stubEnv("COOKIE_SAME_SITE", "weird");
    const res = NextResponse.json({});
    setRefreshCookie(res, "token");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc.toLowerCase()).toContain("samesite=lax");
  });

  it("rejects SameSite=None when secure cookies are disabled", () => {
    vi.stubEnv("COOKIE_SAME_SITE", "none");
    vi.stubEnv("COOKIE_SECURE", "false");
    const res = NextResponse.json({});
    expect(() => setRefreshCookie(res, "token")).toThrow(
      "COOKIE_SAME_SITE=none requires a secure cookie configuration",
    );
  });

  it("setRoleCookie joins roles with comma and sets httpOnly 7-day cookie", () => {
    const res = NextResponse.json({});
    setRoleCookie(res, ["HOST", "CUSTOMER"]);
    const cookie = res.cookies.get(ROLE_COOKIE_NAME);
    expect(cookie?.value).toBe("HOST,CUSTOMER");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain("HttpOnly");
    expect(sc.toLowerCase()).toContain("samesite=lax");
    expect(sc).toContain("Path=/");
    expect(sc).toContain("Max-Age=604800");
  });

  it("clearRoleCookie sets maxAge=0", () => {
    const res = NextResponse.json({});
    clearRoleCookie(res);
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain("Max-Age=0");
    expect(sc).toContain("HttpOnly");
  });
});
