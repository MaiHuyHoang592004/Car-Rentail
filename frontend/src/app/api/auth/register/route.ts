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
  if (
    !body ||
    typeof body.email !== "string" ||
    typeof body.password !== "string" ||
    typeof body.fullName !== "string"
  ) {
    return NextResponse.json(
      {
        code: "VALIDATION_ERROR",
        message: "Email, password, and fullName are required",
      },
      { status: 400 },
    );
  }

  const registerResponse = await callBackend("/auth/register", {
    method: "POST",
    body: {
      email: body.email,
      password: body.password,
      fullName: body.fullName,
      roles: Array.isArray(body.roles) ? body.roles : ["CUSTOMER"],
    },
  });

  if (!registerResponse.ok) {
    return forwardBackendError(registerResponse);
  }

  const loginResponse = await callBackend("/auth/login", {
    method: "POST",
    body: { email: body.email, password: body.password },
  });

  if (!loginResponse.ok) {
    return forwardBackendError(loginResponse);
  }

  const tokens = (await loginResponse.json()) as TokenResponse;
  const payload: SessionPayload = {
    accessToken: tokens.accessToken,
    accessTokenExpiresAt: tokens.accessTokenExpiresAt,
    user: tokens.user,
  };

  const response = NextResponse.json(payload, { status: 201 });
  setRefreshCookie(response, tokens.refreshToken);
  return response;
}
