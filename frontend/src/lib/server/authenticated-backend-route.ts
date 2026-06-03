import "server-only";

import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

import { callBackend } from "@/lib/server/backend";
import {
  REFRESH_COOKIE_NAME,
  clearRefreshCookie,
  clearRoleCookie,
  setRefreshCookie,
  setRoleCookie,
} from "@/lib/server/session-cookie";

type TokenOnlyResponse = {
  accessToken: string;
  refreshToken: string;
};

type SessionUser = {
  roles: string[];
};

const REQUEST_HEADERS_TO_FORWARD = [
  "accept",
  "content-type",
  "idempotency-key",
  "x-correlation-id",
] as const;

function copyResponseHeaders(source: Headers): Headers {
  const headers = new Headers();
  source.forEach((value, key) => {
    if (key.toLowerCase() === "content-length") {
      return;
    }
    headers.set(key, value);
  });
  return headers;
}

async function toNextJsonError(response: Response, fallbackCode = "BACKEND_ERROR") {
  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    payload = {
      code: fallbackCode,
      message: response.statusText || "Backend request failed",
    };
  }

  const headers = copyResponseHeaders(response.headers);
  return NextResponse.json(payload, {
    status: response.status,
    headers,
  });
}

function buildUnauthorizedResponse(message: string) {
  return NextResponse.json(
    { code: "AUTH_INVALID_CREDENTIALS", message },
    { status: 401 },
  );
}

async function refreshServerSession() {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(REFRESH_COOKIE_NAME)?.value;

  if (!refreshToken) {
    return {
      response: buildUnauthorizedResponse("No refresh token"),
    };
  }

  const refreshResponse = await callBackend("/auth/refresh", {
    method: "POST",
    body: { refreshToken },
  });

  if (!refreshResponse.ok) {
    const response = await toNextJsonError(refreshResponse, "AUTH_INVALID_CREDENTIALS");
    clearRefreshCookie(response);
    clearRoleCookie(response);
    return { response };
  }

  const tokens = (await refreshResponse.json()) as TokenOnlyResponse;
  const meResponse = await callBackend("/users/me", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${tokens.accessToken}`,
    },
  });

  if (!meResponse.ok) {
    const response = buildUnauthorizedResponse("Session profile lookup failed");
    clearRefreshCookie(response);
    clearRoleCookie(response);
    return { response };
  }

  const user = (await meResponse.json()) as SessionUser;
  return {
    tokens,
    roles: user.roles,
  };
}

function buildForwardHeaders(request: NextRequest, accessToken: string): Headers {
  const headers = new Headers();
  for (const name of REQUEST_HEADERS_TO_FORWARD) {
    const value = request.headers.get(name);
    if (value) {
      headers.set(name, value);
    }
  }
  headers.set("Authorization", `Bearer ${accessToken}`);
  return headers;
}

async function readRequestBody(request: NextRequest) {
  if (request.method === "GET" || request.method === "HEAD") {
    return undefined;
  }
  const text = await request.text();
  return text.length > 0 ? text : undefined;
}

function finalizeSession(response: NextResponse, refreshToken: string, roles: string[]) {
  setRefreshCookie(response, refreshToken);
  setRoleCookie(response, roles);
  return response;
}

export async function proxyAuthenticatedBackendRequest(
  request: NextRequest,
  backendPath: string,
) {
  const session = await refreshServerSession();
  if ("response" in session) {
    return session.response;
  }

  const backendResponse = await callBackend(backendPath, {
    method: request.method,
    headers: buildForwardHeaders(request, session.tokens.accessToken),
    body: await readRequestBody(request),
  });

  if (!backendResponse.ok) {
    const response = await toNextJsonError(backendResponse);
    return finalizeSession(response, session.tokens.refreshToken, session.roles);
  }

  const headers = copyResponseHeaders(backendResponse.headers);
  const response = new NextResponse(backendResponse.body, {
    status: backendResponse.status,
    headers,
  });
  return finalizeSession(response, session.tokens.refreshToken, session.roles);
}
