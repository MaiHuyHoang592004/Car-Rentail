import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { resetApiClient } from "@/lib/api-client";
import { submitDriverLicense } from "./api";

describe("onboarding api", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetApiClient();
    fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it("submits driver license through the shared /api/v1 rewrite path", async () => {
    const payload = {
      licenseNumber: "eqweqwe",
      licenseExpiryDate: "2026-06-11",
      documentFileId: "7f9c1b2e-3a44-4d0e-9d12-6b8e5f0c1234",
    };

    await submitDriverLicense(payload);

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/users/me/driver-license",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(payload),
      }),
    );
  });
});
