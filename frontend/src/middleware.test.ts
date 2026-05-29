import { describe, expect, it } from "vitest";

import { NextRequest } from "next/server";

import { middleware } from "./middleware";
import { REFRESH_COOKIE_NAME, ROLE_COOKIE_NAME } from "@/lib/session-cookie-shared";

type CookieInput = Record<string, string>;

function makeRequest(pathname: string, cookies: CookieInput = {}): NextRequest {
  const url = `http://localhost${pathname}`;
  const cookieHeader = Object.entries(cookies)
    .map(([k, v]) => `${k}=${v}`)
    .join("; ");
  const headers: HeadersInit = cookieHeader ? { cookie: cookieHeader } : {};
  return new NextRequest(url, { headers });
}

function locationHeader(res: Response): string | null {
  return res.headers.get("location");
}

describe("middleware role-based auth", () => {
  it("passes non-protected route without checking cookies", () => {
    const res = middleware(makeRequest("/listings")) as Response;
    // x-middleware-next header signals NextResponse.next() in Edge
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("redirects to /login when refresh cookie missing on protected route", () => {
    const res = middleware(makeRequest("/host/listings")) as Response;
    const loc = locationHeader(res);
    expect(loc).toBeTruthy();
    expect(loc).toContain("/login");
    expect(loc).toContain("next=%2Fhost%2Flistings");
  });

  it("redirects to /login and clears cookies when refresh present but role missing", () => {
    const res = middleware(
      makeRequest("/host/listings", { [REFRESH_COOKIE_NAME]: "r" }),
    ) as Response;
    expect(locationHeader(res)).toContain("/login");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain(REFRESH_COOKIE_NAME);
    expect(sc).toContain(ROLE_COOKIE_NAME);
  });

  it("redirects authenticated-only route to /login when refresh exists but role cookie is empty", () => {
    const res = middleware(
      makeRequest("/me/profile", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "",
      }),
    ) as Response;
    expect(locationHeader(res)).toContain("/login");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain(REFRESH_COOKIE_NAME);
    expect(sc).toContain(ROLE_COOKIE_NAME);
  });

  it("redirects CUSTOMER to /forbidden when accessing /host", () => {
    const res = middleware(
      makeRequest("/host/listings", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "CUSTOMER",
      }),
    ) as Response;
    expect(locationHeader(res)).toContain("/forbidden");
  });

  it("allows HOST on /host", () => {
    const res = middleware(
      makeRequest("/host/listings", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "HOST",
      }),
    ) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("redirects ADMIN away from /host (HOST-only)", () => {
    const res = middleware(
      makeRequest("/host/listings", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "ADMIN",
      }),
    ) as Response;
    expect(locationHeader(res)).toContain("/forbidden");
  });

  it("allows ADMIN on /admin", () => {
    const res = middleware(
      makeRequest("/admin/users", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "ADMIN",
      }),
    ) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("allows any authenticated user on /me/profile", () => {
    const res = middleware(
      makeRequest("/me/profile", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "HOST",
      }),
    ) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("redirects HOST away from /me/bookings (CUSTOMER-only)", () => {
    const res = middleware(
      makeRequest("/me/bookings", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "HOST",
      }),
    ) as Response;
    expect(locationHeader(res)).toContain("/forbidden");
  });

  it("allows CUSTOMER on /listings/abc/book", () => {
    const res = middleware(
      makeRequest("/listings/abc/book", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "CUSTOMER",
      }),
    ) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("passes /listings/abc (detail page, not /book) without auth check", () => {
    const res = middleware(makeRequest("/listings/abc")) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });
});
