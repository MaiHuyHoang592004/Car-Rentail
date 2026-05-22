import { ApiError, type ApiErrorPayload } from "@/lib/api-error";

const API_PREFIX = "/api/v1";

export type ApiFetchInit = Omit<RequestInit, "body"> & {
  body?: unknown;
  idempotencyKey?: string;
  skipAuth?: boolean;
  skipRefresh?: boolean;
};

export type ApiClient = {
  apiFetch: <T = unknown>(path: string, init?: ApiFetchInit) => Promise<T>;
  api: {
    get: <T = unknown>(path: string, init?: Omit<ApiFetchInit, "body">) => Promise<T>;
    post: <T = unknown>(path: string, body?: unknown, init?: ApiFetchInit) => Promise<T>;
    patch: <T = unknown>(path: string, body?: unknown, init?: ApiFetchInit) => Promise<T>;
    put: <T = unknown>(path: string, body?: unknown, init?: ApiFetchInit) => Promise<T>;
    delete: <T = unknown>(path: string, init?: ApiFetchInit) => Promise<T>;
  };
  setAccessTokenGetter: (getter: () => string | null) => void;
  setRefreshHandler: (handler: () => Promise<boolean>) => void;
  setAuthFailedHandler: (handler: () => void) => void;
  reset: () => void;
};

async function parseError(response: Response): Promise<ApiError> {
  let payload: ApiErrorPayload;
  try {
    payload = (await response.json()) as ApiErrorPayload;
  } catch {
    payload = {
      code: response.status >= 500 ? "INTERNAL_ERROR" : "REQUEST_FAILED",
      message: response.statusText || "Request failed",
    };
  }
  return new ApiError(response.status, payload);
}

export function createApiClient(): ApiClient {
  let accessTokenGetter: () => string | null = () => null;
  let refreshHandler: (() => Promise<boolean>) | null = null;
  let onAuthFailed: (() => void) | null = null;

  async function executeFetch<T>(
    path: string,
    init: ApiFetchInit,
    isRetry: boolean,
  ): Promise<T> {
    const url = path.startsWith("http") ? path : `${API_PREFIX}${path}`;
    const headers = new Headers(init.headers);
    if (init.body !== undefined && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    if (!headers.has("Accept")) {
      headers.set("Accept", "application/json");
    }
    if (init.idempotencyKey) {
      headers.set("Idempotency-Key", init.idempotencyKey);
    }
    if (!init.skipAuth) {
      const token = accessTokenGetter();
      if (token) {
        headers.set("Authorization", `Bearer ${token}`);
      }
    }

    let response: Response;
    try {
      response = await fetch(url, {
        ...init,
        headers,
        body:
          init.body === undefined
            ? undefined
            : typeof init.body === "string"
              ? init.body
              : JSON.stringify(init.body),
      });
    } catch (err) {
      throw ApiError.network(err instanceof Error ? err.message : "Network error");
    }

    if (response.status === 401 && !init.skipRefresh && !isRetry && refreshHandler) {
      const refreshed = await refreshHandler();
      if (refreshed) {
        return executeFetch<T>(path, init, true);
      }
      if (onAuthFailed) {
        onAuthFailed();
      }
    }

    if (!response.ok) {
      throw await parseError(response);
    }

    if (response.status === 204) {
      return undefined as T;
    }

    const contentType = response.headers.get("Content-Type") ?? "";
    if (!contentType.includes("application/json")) {
      return undefined as T;
    }

    return (await response.json()) as T;
  }

  function apiFetch<T = unknown>(path: string, init: ApiFetchInit = {}): Promise<T> {
    return executeFetch<T>(path, init, false);
  }

  const api = {
    get: <T = unknown>(path: string, init: Omit<ApiFetchInit, "body"> = {}) =>
      apiFetch<T>(path, { ...init, method: "GET" }),
    post: <T = unknown>(path: string, body?: unknown, init: ApiFetchInit = {}) =>
      apiFetch<T>(path, { ...init, method: "POST", body }),
    patch: <T = unknown>(path: string, body?: unknown, init: ApiFetchInit = {}) =>
      apiFetch<T>(path, { ...init, method: "PATCH", body }),
    put: <T = unknown>(path: string, body?: unknown, init: ApiFetchInit = {}) =>
      apiFetch<T>(path, { ...init, method: "PUT", body }),
    delete: <T = unknown>(path: string, init: ApiFetchInit = {}) =>
      apiFetch<T>(path, { ...init, method: "DELETE" }),
  };

  return {
    apiFetch,
    api,
    setAccessTokenGetter(getter) {
      accessTokenGetter = getter;
    },
    setRefreshHandler(handler) {
      refreshHandler = handler;
    },
    setAuthFailedHandler(handler) {
      onAuthFailed = handler;
    },
    reset() {
      accessTokenGetter = () => null;
      refreshHandler = null;
      onAuthFailed = null;
    },
  };
}

// Default singleton — preserves existing import paths.
const defaultClient = createApiClient();

export const apiFetch = defaultClient.apiFetch;
export const api = defaultClient.api;

export function registerAccessTokenGetter(getter: () => string | null) {
  defaultClient.setAccessTokenGetter(getter);
}

export function registerRefreshHandler(handler: () => Promise<boolean>) {
  defaultClient.setRefreshHandler(handler);
}

export function registerAuthFailedHandler(handler: () => void) {
  defaultClient.setAuthFailedHandler(handler);
}

/** Reset the default client's handlers. Intended for test isolation. */
export function resetApiClient() {
  defaultClient.reset();
}
