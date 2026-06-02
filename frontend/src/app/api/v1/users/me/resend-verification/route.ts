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

type SessionUser = {
  roles: string[];
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

  const refreshResponse = await callBackend("/auth/refresh", {
    method: "POST",
    body: { refreshToken },
  });

  if (!refreshResponse.ok) {
    const response = await forwardBackendError(refreshResponse);
    clearRefreshCookie(response);
    clearRoleCookie(response);
    return response;
  }

  const tokens = (await refreshResponse.json()) as TokenOnlyResponse;
  const meResponse = await callBackend("/users/me", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${tokens.accessToken}`,
    },
  });

  if (!meResponse.ok) {
    const response = new NextResponse(null, { status: 401 });
    clearRefreshCookie(response);
    clearRoleCookie(response);
    return response;
  }

  const user = (await meResponse.json()) as SessionUser;
  const resendResponse = await callBackend("/users/me/resend-verification", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${tokens.accessToken}`,
    },
  });

  if (!resendResponse.ok) {
    return forwardBackendError(resendResponse);
  }

  const response = new NextResponse(null, { status: 204 });
  setRefreshCookie(response, tokens.refreshToken);
  setRoleCookie(response, user.roles);
  return response;
}
