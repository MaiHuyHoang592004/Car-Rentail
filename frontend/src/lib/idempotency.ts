import { useRef } from "react";

function generateUuid(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function useIdempotencyKey(): string {
  const ref = useRef<string | null>(null);
  if (ref.current === null) {
    ref.current = generateUuid();
  }
  return ref.current;
}

export function newIdempotencyKey(): string {
  return generateUuid();
}
