import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { callBackend } from "@/lib/server/backend";
import {
  REFRESH_COOKIE_NAME,
  clearRefreshCookie,
  setRefreshCookie,
  type SessionPayload,
  type SessionUser,
} from "@/lib/server/session-cookie";

type TokenOnlyResponse = {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
};

export async function GET() {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(REFRESH_COOKIE_NAME)?.value;

  if (!refreshToken) {
    return new NextResponse(null, { status: 204 });
  }

  const refreshResponse = await callBackend("/auth/refresh", {
    method: "POST",
    body: { refreshToken },
  });

  if (!refreshResponse.ok) {
    const response = new NextResponse(null, { status: 204 });
    clearRefreshCookie(response);
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
    const response = new NextResponse(null, { status: 204 });
    clearRefreshCookie(response);
    return response;
  }

  const user = (await meResponse.json()) as SessionUser;
  const payload: SessionPayload = {
    accessToken: tokens.accessToken,
    accessTokenExpiresAt: tokens.accessTokenExpiresAt,
    user,
  };

  const response = NextResponse.json(payload, { status: 200 });
  setRefreshCookie(response, tokens.refreshToken);
  return response;
}
