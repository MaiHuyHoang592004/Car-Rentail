import type { NextRequest } from "next/server";

import { proxyAuthenticatedBackendRequest } from "@/lib/server/authenticated-backend-route";

export async function POST(request: NextRequest) {
  return proxyAuthenticatedBackendRequest(
    request,
    `/files/dispute-attachments/upload-intents${request.nextUrl.search}`,
  );
}
