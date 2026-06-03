import { act, render, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

const routerReplace = vi.fn();
const routerRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: routerReplace, refresh: routerRefresh, push: vi.fn() }),
}));

import { AuthProvider, useAuth } from "./auth-context";

const sessionUser = {
  id: "u-1",
  email: "u@e.com",
  emailVerified: false,
  roles: ["CUSTOMER"],
  fullName: "U",
  phone: null,
  dateOfBirth: null,
  addressLine: null,
  driverVerificationStatus: "NOT_SUBMITTED",
};

function wrapperWithInitial(initial: Parameters<typeof AuthProvider>[0]["initialSession"]) {
  function InitialAuthWrapper({ children }: { children: ReactNode }) {
    return (
    <AuthProvider initialSession={initial}>{children}</AuthProvider>
    );
  }

  return InitialAuthWrapper;
}

describe("AuthProvider", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    routerReplace.mockClear();
    routerRefresh.mockClear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("starts authenticated when initialSession provided and exposes roles", () => {
    fetchSpy.mockResolvedValue(new Response(null, { status: 204 }));
    const { result } = renderHook(() => useAuth(), {
      wrapper: wrapperWithInitial({
        accessToken: "ACCESS",
        accessTokenExpiresAt: "2099-01-01T00:00:00Z",
        user: sessionUser,
      }),
    });
    expect(result.current.status).toBe("authenticated");
    expect(result.current.user?.id).toBe("u-1");
    expect(result.current.hasRole("CUSTOMER")).toBe(true);
    expect(result.current.hasRole("ADMIN")).toBe(false);
  });

  it("rehydrates from /api/auth/session on mount when no initial session", async () => {
    fetchSpy.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          accessToken: "REHYDRATED",
          accessTokenExpiresAt: "2099-01-01T00:00:00Z",
          user: sessionUser,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    const { result } = renderHook(() => useAuth(), {
      wrapper: ({ children }) => <AuthProvider>{children}</AuthProvider>,
    });
    await waitFor(() => expect(result.current.status).toBe("authenticated"));
    expect(result.current.user?.email).toBe("u@e.com");
    expect(fetchSpy).toHaveBeenCalledWith("/api/auth/session", expect.objectContaining({ method: "GET" }));
  });

  it("becomes guest when /api/auth/session returns 204", async () => {
    fetchSpy.mockResolvedValueOnce(new Response(null, { status: 204 }));
    const { result } = renderHook(() => useAuth(), {
      wrapper: ({ children }) => <AuthProvider>{children}</AuthProvider>,
    });
    await waitFor(() => expect(result.current.status).toBe("guest"));
    expect(result.current.user).toBeNull();
  });

  it("login() posts to /api/auth/login and sets authenticated state", async () => {
    fetchSpy
      .mockResolvedValueOnce(new Response(null, { status: 204 })) // initial session probe
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            accessToken: "ACCESS",
            accessTokenExpiresAt: "2099-01-01T00:00:00Z",
            user: sessionUser,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      );
    const { result } = renderHook(() => useAuth(), {
      wrapper: ({ children }) => <AuthProvider>{children}</AuthProvider>,
    });
    await waitFor(() => expect(result.current.status).toBe("guest"));

    await act(async () => {
      await result.current.login({ email: "u@e.com", password: "pw" });
    });
    expect(result.current.status).toBe("authenticated");
    expect(result.current.user?.id).toBe("u-1");
    const loginCall = fetchSpy.mock.calls.find(([url]) => url === "/api/auth/login");
    expect(loginCall).toBeDefined();
    expect((loginCall![1] as RequestInit).method).toBe("POST");
  });

  it("logout() clears session and navigates to /", async () => {
    fetchSpy.mockResolvedValue(new Response(null, { status: 204 }));
    const { result } = renderHook(() => useAuth(), {
      wrapper: wrapperWithInitial({
        accessToken: "ACCESS",
        accessTokenExpiresAt: "2099-01-01T00:00:00Z",
        user: sessionUser,
      }),
    });
    expect(result.current.status).toBe("authenticated");
    await act(async () => {
      await result.current.logout();
    });
    expect(result.current.status).toBe("guest");
    expect(result.current.user).toBeNull();
    expect(routerReplace).toHaveBeenCalledWith("/");
  });

  it("logoutAll() revokes server sessions then clears local session", async () => {
    fetchSpy
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    const { result } = renderHook(() => useAuth(), {
      wrapper: wrapperWithInitial({
        accessToken: "ACCESS",
        accessTokenExpiresAt: "2099-01-01T00:00:00Z",
        user: sessionUser,
      }),
    });

    await act(async () => {
      await result.current.logoutAll();
    });

    expect(fetchSpy).toHaveBeenNthCalledWith(
      1,
      "/api/v1/auth/logout-all",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Authorization: "Bearer ACCESS",
          Accept: "application/json",
        }),
      }),
    );
    expect(fetchSpy).toHaveBeenNthCalledWith(
      2,
      "/api/auth/logout",
      expect.objectContaining({ method: "POST" }),
    );
    expect(result.current.status).toBe("guest");
    expect(routerReplace).toHaveBeenCalledWith("/");
  });

  it("useAuth throws when used outside AuthProvider", () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => render(<TestConsumer />)).toThrow(/AuthProvider/);
    spy.mockRestore();
  });
});

function TestConsumer() {
  useAuth();
  return null;
}
