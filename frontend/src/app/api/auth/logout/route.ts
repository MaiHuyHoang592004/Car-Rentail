import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { callBackend } from "@/lib/server/backend";
import { REFRESH_COOKIE_NAME, clearRefreshCookie } from "@/lib/server/session-cookie";

export async function POST() {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(REFRESH_COOKIE_NAME)?.value;

  if (refreshToken) {
    await callBackend("/auth/logout", {
      method: "POST",
      body: { refreshToken },
    }).catch(() => undefined);
  }

  const response = new NextResponse(null, { status: 204 });
  clearRefreshCookie(response);
  return response;
}
