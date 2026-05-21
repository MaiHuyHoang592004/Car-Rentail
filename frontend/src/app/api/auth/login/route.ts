import { NextResponse } from "next/server";

import { callBackend, forwardBackendError } from "@/lib/server/backend";
import {
  setRefreshCookie,
  type SessionPayload,
  type SessionUser,
} from "@/lib/server/session-cookie";

type TokenResponse = {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  user: SessionUser;
};

export async function POST(request: Request) {
  const body = await request.json().catch(() => null);
  if (!body || typeof body.email !== "string" || typeof body.password !== "string") {
    return NextResponse.json(
      {
        code: "VALIDATION_ERROR",
        message: "Email and password are required",
      },
      { status: 400 },
    );
  }

  const response = await callBackend("/auth/login", {
    method: "POST",
    body: { email: body.email, password: body.password },
  });

  if (!response.ok) {
    return forwardBackendError(response);
  }

  const tokens = (await response.json()) as TokenResponse;
  const payload: SessionPayload = {
    accessToken: tokens.accessToken,
    accessTokenExpiresAt: tokens.accessTokenExpiresAt,
    user: tokens.user,
  };

  const nextResponse = NextResponse.json(payload, { status: 200 });
  setRefreshCookie(nextResponse, tokens.refreshToken);
  return nextResponse;
}
