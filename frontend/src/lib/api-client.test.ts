import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  api,
  apiFetch,
  createApiClient,
  resetApiClient,
  setActiveApiClient,
} from "./api-client";
import { ApiError } from "./api-error";

function mockResponse(
  status: number,
  body: unknown = null,
  contentType = "application/json",
): Response {
  const init: ResponseInit = {
    status,
    headers: contentType ? { "Content-Type": contentType } : undefined,
  };
  if (status === 204 || body === null) {
    return new Response(null, init);
  }
  return new Response(JSON.stringify(body), init);
}

describe("api-client", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    resetApiClient();
    const client = createApiClient();
    client.setRefreshHandler(async () => false);
    setActiveApiClient(client);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it("prefixes path with /api/v1", async () => {
    fetchSpy.mockResolvedValue(mockResponse(200, { ok: true }));
    await api.get("/users/me");
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/v1/users/me");
  });

  it("attaches Bearer when token getter returns a token", async () => {
    const client = createApiClient();
    client.setAccessTokenGetter(() => "tok-123");
    setActiveApiClient(client);
    fetchSpy.mockResolvedValue(mockResponse(200, { ok: true }));
    await api.get("/users/me");
    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect((init.headers as Headers).get("Authorization")).toBe("Bearer tok-123");
  });

  it("skips Authorization when skipAuth=true", async () => {
    const client = createApiClient();
    client.setAccessTokenGetter(() => "tok-123");
    setActiveApiClient(client);
    fetchSpy.mockResolvedValue(mockResponse(200, { ok: true }));
    await apiFetch("/auth/login", { method: "POST", body: { a: 1 }, skipAuth: true });
    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect((init.headers as Headers).has("Authorization")).toBe(false);
  });

  it("sets Idempotency-Key when provided", async () => {
    fetchSpy.mockResolvedValue(mockResponse(200, { ok: true }));
    await api.post("/bookings", { x: 1 }, { idempotencyKey: "abc-123" });
    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect((init.headers as Headers).get("Idempotency-Key")).toBe("abc-123");
  });

  it("serializes JSON body and sets Content-Type", async () => {
    fetchSpy.mockResolvedValue(mockResponse(200, { ok: true }));
    await api.post("/x", { a: 1 });
    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect((init.headers as Headers).get("Content-Type")).toBe("application/json");
    expect(init.body).toBe(JSON.stringify({ a: 1 }));
  });

  it("retries once after a successful refresh on 401", async () => {
    const client = createApiClient();
    client.setAccessTokenGetter(() => "tok-1");
    const refresh = vi.fn().mockResolvedValue(true);
    client.setRefreshHandler(refresh);
    setActiveApiClient(client);

    fetchSpy
      .mockResolvedValueOnce(mockResponse(401, { code: "AUTH", message: "expired" }))
      .mockResolvedValueOnce(mockResponse(200, { ok: true }));

    const result = await api.get<{ ok: boolean }>("/users/me");
    expect(result).toEqual({ ok: true });
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(fetchSpy).toHaveBeenCalledTimes(2);
  });

  it("calls authFailed handler when refresh fails on 401", async () => {
    const client = createApiClient();
    const refresh = vi.fn().mockResolvedValue(false);
    const onFail = vi.fn();
    client.setRefreshHandler(refresh);
    client.setAuthFailedHandler(onFail);
    setActiveApiClient(client);

    fetchSpy.mockResolvedValue(mockResponse(401, { code: "AUTH", message: "expired" }));

    await expect(api.get("/users/me")).rejects.toBeInstanceOf(ApiError);
    expect(onFail).toHaveBeenCalledTimes(1);
  });

  it("does not retry when skipRefresh=true", async () => {
    const client = createApiClient();
    const refresh = vi.fn().mockResolvedValue(true);
    client.setRefreshHandler(refresh);
    setActiveApiClient(client);
    fetchSpy.mockResolvedValue(mockResponse(401, { code: "AUTH", message: "x" }));

    await expect(apiFetch("/users/me", { skipRefresh: true })).rejects.toBeInstanceOf(ApiError);
    expect(refresh).not.toHaveBeenCalled();
  });

  it("throws ApiError with parsed payload on non-2xx", async () => {
    fetchSpy.mockResolvedValue(
      mockResponse(422, { code: "VALIDATION_ERROR", message: "bad", details: [] }),
    );
    await expect(api.get("/x")).rejects.toMatchObject({
      status: 422,
      code: "VALIDATION_ERROR",
      message: "bad",
    });
  });

  it("wraps network failures in ApiError(0, NETWORK_ERROR)", async () => {
    fetchSpy.mockRejectedValue(new TypeError("Failed to fetch"));
    await expect(api.get("/x")).rejects.toMatchObject({
      status: 0,
      code: "NETWORK_ERROR",
    });
  });

  it("returns undefined for 204 responses", async () => {
    fetchSpy.mockResolvedValue(mockResponse(204));
    const result = await api.delete("/x");
    expect(result).toBeUndefined();
  });

  it("createApiClient returns isolated instances (no shared mutable state)", async () => {
    const clientA = createApiClient();
    const clientB = createApiClient();
    clientA.setAccessTokenGetter(() => "tok-A");
    clientB.setAccessTokenGetter(() => "tok-B");

    fetchSpy
      .mockResolvedValueOnce(mockResponse(200, { ok: true }))
      .mockResolvedValueOnce(mockResponse(200, { ok: true }));

    await clientA.api.get("/me");
    await clientB.api.get("/me");

    const initA = fetchSpy.mock.calls[0][1] as RequestInit;
    const initB = fetchSpy.mock.calls[1][1] as RequestInit;
    expect((initA.headers as Headers).get("Authorization")).toBe("Bearer tok-A");
    expect((initB.headers as Headers).get("Authorization")).toBe("Bearer tok-B");
  });

  it("resetApiClient clears the default client's token getter", async () => {
    const client = createApiClient();
    client.setAccessTokenGetter(() => "tok-X");
    setActiveApiClient(client);
    resetApiClient();
    fetchSpy.mockResolvedValue(mockResponse(200, { ok: true }));
    await api.get("/me");
    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect((init.headers as Headers).has("Authorization")).toBe(false);
  });

  it("throws and skips fetch when signal is already aborted", async () => {
    const ctrl = new AbortController();
    ctrl.abort();
    await expect(api.get("/x", { signal: ctrl.signal })).rejects.toBeInstanceOf(ApiError);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("forwards signal to fetch", async () => {
    fetchSpy.mockResolvedValue(mockResponse(200, { ok: true }));
    const ctrl = new AbortController();
    await api.get("/x", { signal: ctrl.signal });
    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect(init.signal).toBe(ctrl.signal);
  });
});
