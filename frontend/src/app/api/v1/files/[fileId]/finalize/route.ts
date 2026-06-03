import type { NextRequest } from "next/server";

import { proxyAuthenticatedBackendRequest } from "@/lib/server/authenticated-backend-route";

type RouteContext = {
  params: Promise<{
    fileId: string;
  }>;
};

export async function POST(request: NextRequest, context: RouteContext) {
  const params = await context.params;
  return proxyAuthenticatedBackendRequest(
    request,
    `/files/${params.fileId}/finalize${request.nextUrl.search}`,
  );
}
