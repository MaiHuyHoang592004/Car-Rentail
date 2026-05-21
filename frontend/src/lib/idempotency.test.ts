import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { newIdempotencyKey, useIdempotencyKey } from "./idempotency";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

describe("idempotency keys", () => {
  it("newIdempotencyKey produces UUID v4", () => {
    const key = newIdempotencyKey();
    expect(key).toMatch(UUID_RE);
  });

  it("each call returns a different key", () => {
    const a = newIdempotencyKey();
    const b = newIdempotencyKey();
    expect(a).not.toBe(b);
  });

  it("useIdempotencyKey is stable across re-renders", () => {
    const { result, rerender } = renderHook(() => useIdempotencyKey());
    const first = result.current;
    expect(first).toMatch(UUID_RE);
    rerender();
    expect(result.current).toBe(first);
  });
});
