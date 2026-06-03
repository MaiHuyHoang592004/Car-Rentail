import type { NextRequest } from "next/server";

import { proxyAuthenticatedBackendRequest } from "@/lib/server/authenticated-backend-route";

export async function GET(request: NextRequest) {
  return proxyAuthenticatedBackendRequest(
    request,
    `/payment-banks${request.nextUrl.search}`,
  );
}
