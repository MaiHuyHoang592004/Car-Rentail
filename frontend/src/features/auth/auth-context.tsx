"use client";

import { useRouter } from "next/navigation";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

import { ApiError } from "@/lib/api-error";
import {
  registerAccessTokenGetter,
  registerAuthFailedHandler,
  registerRefreshHandler,
} from "@/lib/api-client";

export type AuthRole = "CUSTOMER" | "HOST" | "ADMIN";

export type AuthUser = {
  id: string;
  email: string;
  roles: string[];
  fullName: string;
  phone: string | null;
  dateOfBirth: string | null;
  addressLine: string | null;
  driverVerificationStatus: string;
};

export type AuthStatus = "loading" | "authenticated" | "guest";

type SessionPayload = {
  accessToken: string;
  accessTokenExpiresAt: string;
  user: AuthUser;
};

type LoginInput = {
  email: string;
  password: string;
};

type RegisterInput = {
  email: string;
  password: string;
  fullName: string;
  roles: AuthRole[];
};

type AuthContextValue = {
  status: AuthStatus;
  user: AuthUser | null;
  roles: string[];
  hasRole: (role: AuthRole | string) => boolean;
  login: (input: LoginInput) => Promise<AuthUser>;
  register: (input: RegisterInput) => Promise<AuthUser>;
  logout: () => Promise<void>;
  refresh: () => Promise<boolean>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

async function readJsonOrError(response: Response): Promise<unknown> {
  if (response.status === 204) {
    return null;
  }
  try {
    return await response.json();
  } catch {
    return null;
  }
}

async function postBff<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method: "POST",
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const payload = await readJsonOrError(response);
  if (!response.ok) {
    const err = payload as { code?: string; message?: string; details?: { field: string; message: string }[]; correlationId?: string } | null;
    throw new ApiError(response.status, {
      code: err?.code ?? "REQUEST_FAILED",
      message: err?.message ?? response.statusText,
      details: err?.details,
      correlationId: err?.correlationId,
    });
  }
  return payload as T;
}

export function AuthProvider({
  children,
  initialSession,
}: {
  children: ReactNode;
  initialSession?: SessionPayload | null;
}) {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(initialSession?.user ?? null);
  const [status, setStatus] = useState<AuthStatus>(initialSession ? "authenticated" : "loading");
  const accessTokenRef = useRef<string | null>(initialSession?.accessToken ?? null);
  const refreshInFlightRef = useRef<Promise<boolean> | null>(null);

  const applySession = useCallback((payload: SessionPayload) => {
    accessTokenRef.current = payload.accessToken;
    setUser(payload.user);
    setStatus("authenticated");
  }, []);

  const clearSession = useCallback(() => {
    accessTokenRef.current = null;
    setUser(null);
    setStatus("guest");
  }, []);

  const refresh = useCallback(async (): Promise<boolean> => {
    if (refreshInFlightRef.current) {
      return refreshInFlightRef.current;
    }
    const promise = (async () => {
      try {
        const response = await fetch("/api/auth/refresh", { method: "POST" });
        if (!response.ok) {
          return false;
        }
        const payload = (await response.json()) as {
          accessToken: string;
          accessTokenExpiresAt: string;
        };
        accessTokenRef.current = payload.accessToken;
        return true;
      } catch {
        return false;
      } finally {
        refreshInFlightRef.current = null;
      }
    })();
    refreshInFlightRef.current = promise;
    return promise;
  }, []);

  const login = useCallback(
    async (input: LoginInput): Promise<AuthUser> => {
      const session = await postBff<SessionPayload>("/api/auth/login", input);
      applySession(session);
      return session.user;
    },
    [applySession],
  );

  const register = useCallback(
    async (input: RegisterInput): Promise<AuthUser> => {
      const session = await postBff<SessionPayload>("/api/auth/register", input);
      applySession(session);
      return session.user;
    },
    [applySession],
  );

  const logout = useCallback(async (): Promise<void> => {
    try {
      await fetch("/api/auth/logout", { method: "POST" });
    } catch {
      // ignore, clear local state regardless
    }
    clearSession();
    router.replace("/");
    router.refresh();
  }, [clearSession, router]);

  const hasRole = useCallback(
    (role: AuthRole | string) => (user?.roles ?? []).includes(role),
    [user],
  );

  useEffect(() => {
    registerAccessTokenGetter(() => accessTokenRef.current);
    registerRefreshHandler(refresh);
    registerAuthFailedHandler(() => {
      clearSession();
    });
  }, [refresh, clearSession]);

  useEffect(() => {
    if (initialSession) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const response = await fetch("/api/auth/session", { method: "GET" });
        if (cancelled) return;
        if (response.status === 200) {
          const payload = (await response.json()) as SessionPayload;
          applySession(payload);
        } else {
          clearSession();
        }
      } catch {
        if (!cancelled) {
          clearSession();
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [initialSession, applySession, clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      roles: user?.roles ?? [],
      hasRole,
      login,
      register,
      logout,
      refresh,
    }),
    [status, user, hasRole, login, register, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
