import "server-only";

import { NextResponse } from "next/server";

const BACKEND_URL = process.env.API_BACKEND_URL ?? "http://localhost:8087";
const API_PREFIX = "/api/v1";

type BackendInit = Omit<RequestInit, "body"> & {
  body?: unknown;
};

export async function callBackend(path: string, init: BackendInit = {}): Promise<Response> {
  const url = `${BACKEND_URL}${API_PREFIX}${path}`;
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  return fetch(url, {
    ...init,
    headers,
    body:
      init.body === undefined
        ? undefined
        : typeof init.body === "string"
          ? init.body
          : JSON.stringify(init.body),
    cache: "no-store",
  });
}

export async function forwardBackendError(response: Response, fallbackCode = "BACKEND_ERROR") {
  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    payload = {
      code: fallbackCode,
      message: response.statusText || "Backend request failed",
    };
  }
  return NextResponse.json(payload, { status: response.status });
}
