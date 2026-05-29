import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { adminListUsers } from "./api";

describe("admin users api", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  }

  it("sends filters and maps user list", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        content: [
          {
            id: "u-1",
            email: "a@b.com",
            roles: ["CUSTOMER"],
            fullName: "User A",
            status: "ACTIVE",
            driverVerificationStatus: "NOT_SUBMITTED",
            createdAt: "2026-06-01T00:00:00Z",
            lastLoginAt: "2026-06-15T00:00:00Z",
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    );

    const result = await adminListUsers({
      status: "ACTIVE",
      role: "CUSTOMER",
      page: 0,
      size: 20,
    });

    expect(result.users).toHaveLength(1);
    expect(result.users[0].email).toBe("a@b.com");
    expect(result.users[0].roles).toEqual(["CUSTOMER"]);
    expect(result.users[0].lastLoginAt).toBe("2026-06-15T00:00:00Z");

    const url = fetchSpy.mock.calls[0][0] as string;
    expect(url).toContain("status=ACTIVE");
    expect(url).toContain("role=CUSTOMER");
    expect(url).toContain("page=0");
    expect(url).toContain("size=20");
  });

  it("omits status and role when ALL", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    );
    await adminListUsers({ status: "ALL", role: "ALL" });
    const url = fetchSpy.mock.calls[0][0] as string;
    expect(url).not.toContain("status=");
    expect(url).not.toContain("role=");
  });

  it("handles null lastLoginAt", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        content: [
          {
            id: "u-2",
            email: "c@d.com",
            roles: ["HOST"],
            fullName: "User C",
            status: "ACTIVE",
            driverVerificationStatus: "APPROVED",
            createdAt: "2026-06-01T00:00:00Z",
            lastLoginAt: null,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    );
    const result = await adminListUsers({ role: "HOST" });
    expect(result.users[0].lastLoginAt).toBeNull();
  });
});