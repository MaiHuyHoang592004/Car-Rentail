import "server-only";

import type { NextResponse } from "next/server";

import {
  getRefreshCookieName,
  REFRESH_COOKIE_NAME,
  ROLE_COOKIE_NAME,
} from "@/lib/session-cookie-shared";

export { REFRESH_COOKIE_NAME, ROLE_COOKIE_NAME };

const SEVEN_DAYS_SECONDS = 7 * 24 * 60 * 60;
type CookieSameSite = "lax" | "strict" | "none";

function resolveSecureCookie(): boolean {
  const configured = process.env.COOKIE_SECURE;
  if (configured === "true") {
    return true;
  }
  if (configured === "false") {
    return false;
  }
  return process.env.NODE_ENV !== "development";
}

function resolveSameSite(): CookieSameSite {
  const configured = process.env.COOKIE_SAME_SITE?.trim().toLowerCase();
  if (configured === "strict" || configured === "none") {
    return configured;
  }
  return "lax";
}

function assertCookiePolicy(sameSite: CookieSameSite, secure: boolean) {
  if (sameSite === "none" && !secure) {
    throw new Error("COOKIE_SAME_SITE=none requires a secure cookie configuration");
  }
}

function sharedCookieOptions(maxAge: number) {
  const secure = resolveSecureCookie();
  const sameSite = resolveSameSite();
  assertCookiePolicy(sameSite, secure);
  return {
    httpOnly: true,
    secure,
    sameSite,
    path: "/",
    maxAge,
  } as const;
}

export function setRefreshCookie(response: NextResponse, refreshToken: string) {
  response.cookies.set({
    name: getRefreshCookieName(),
    value: refreshToken,
    ...sharedCookieOptions(SEVEN_DAYS_SECONDS),
  });
}

export function clearRefreshCookie(response: NextResponse) {
  response.cookies.set({
    name: getRefreshCookieName(),
    value: "",
    ...sharedCookieOptions(0),
  });
}

export function setRoleCookie(response: NextResponse, roles: string[]) {
  response.cookies.set({
    name: ROLE_COOKIE_NAME,
    value: roles.join(","),
    ...sharedCookieOptions(SEVEN_DAYS_SECONDS),
  });
}

export function clearRoleCookie(response: NextResponse) {
  response.cookies.set({
    name: ROLE_COOKIE_NAME,
    value: "",
    ...sharedCookieOptions(0),
  });
}

export type SessionUser = {
  id: string;
  email: string;
  emailVerified: boolean;
  roles: string[];
  fullName: string;
  phone: string | null;
  dateOfBirth: string | null;
  addressLine: string | null;
  driverVerificationStatus: string;
};

export type SessionPayload = {
  accessToken: string;
  accessTokenExpiresAt: string;
  user: SessionUser;
};
