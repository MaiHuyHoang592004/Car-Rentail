import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { callBackend, forwardBackendError } from "@/lib/server/backend";
import {
  REFRESH_COOKIE_NAME,
  clearRefreshCookie,
  clearRoleCookie,
  setRefreshCookie,
  setRoleCookie,
} from "@/lib/server/session-cookie";

type TokenOnlyResponse = {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
};

export async function POST() {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(REFRESH_COOKIE_NAME)?.value;

  if (!refreshToken) {
    return NextResponse.json(
      { code: "AUTH_INVALID_CREDENTIALS", message: "No refresh token" },
      { status: 401 },
    );
  }

  const response = await callBackend("/auth/refresh", {
    method: "POST",
    body: { refreshToken },
  });

  if (!response.ok) {
    const errorResponse = await forwardBackendError(response);
    clearRefreshCookie(errorResponse);
    clearRoleCookie(errorResponse);
    return errorResponse;
  }

  const tokens = (await response.json()) as TokenOnlyResponse;
  const meResponse = await callBackend("/users/me", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${tokens.accessToken}`,
    },
  });

  if (!meResponse.ok) {
    const cleared = new NextResponse(null, { status: 204 });
    clearRefreshCookie(cleared);
    clearRoleCookie(cleared);
    return cleared;
  }

  const user = (await meResponse.json()) as { roles: string[] };
  const nextResponse = NextResponse.json(
    {
      accessToken: tokens.accessToken,
      accessTokenExpiresAt: tokens.accessTokenExpiresAt,
    },
    { status: 200 },
  );
  setRefreshCookie(nextResponse, tokens.refreshToken);
  setRoleCookie(nextResponse, user.roles);
  return nextResponse;
}
