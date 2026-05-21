import { NextRequest, NextResponse } from "next/server";

import { REFRESH_COOKIE_NAME } from "@/lib/server/session-cookie";

const PROTECTED_PREFIXES = ["/me", "/host", "/admin", "/bookings"];

function needsAuth(pathname: string): boolean {
  if (PROTECTED_PREFIXES.some((prefix) => pathname.startsWith(prefix))) {
    return true;
  }
  if (/^\/listings\/[^/]+\/book\/?$/.test(pathname)) {
    return true;
  }
  return false;
}

export function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  if (!needsAuth(pathname)) {
    return NextResponse.next();
  }

  const refreshCookie = request.cookies.get(REFRESH_COOKIE_NAME)?.value;
  if (refreshCookie) {
    return NextResponse.next();
  }

  const loginUrl = request.nextUrl.clone();
  loginUrl.pathname = "/login";
  loginUrl.search = "";
  loginUrl.searchParams.set("next", `${pathname}${search}`);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: [
    "/me/:path*",
    "/host/:path*",
    "/admin/:path*",
    "/bookings/:path*",
    "/listings/:id/book",
  ],
};
