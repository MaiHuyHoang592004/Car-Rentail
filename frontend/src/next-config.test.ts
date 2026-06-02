import { describe, expect, it, vi } from "vitest";

describe("next.config rewrites", () => {
  it("rewrites /api/v1 requests directly to the backend", async () => {
    vi.resetModules();
    vi.stubEnv("API_BACKEND_URL", "http://localhost:8087");

    const { default: nextConfig } = await import("../next.config");
    const rewrites = await nextConfig.rewrites?.();

    expect(rewrites).toEqual([
      {
        source: "/api/v1/:path*",
        destination: "http://localhost:8087/api/v1/:path*",
      },
    ]);
  });
});
