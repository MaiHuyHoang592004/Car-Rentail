import "server-only";

import type { NextResponse } from "next/server";

export const REFRESH_COOKIE_NAME = "rentflow_refresh";

const SEVEN_DAYS_SECONDS = 7 * 24 * 60 * 60;

export function setRefreshCookie(response: NextResponse, refreshToken: string) {
  response.cookies.set({
    name: REFRESH_COOKIE_NAME,
    value: refreshToken,
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: SEVEN_DAYS_SECONDS,
  });
}

export function clearRefreshCookie(response: NextResponse) {
  response.cookies.set({
    name: REFRESH_COOKIE_NAME,
    value: "",
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: 0,
  });
}

export type SessionUser = {
  id: string;
  email: string;
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
