function resolveSecureCookie(): boolean {
  const configured = process.env.COOKIE_SECURE;
  if (configured === "true") {
    return true;
  }
  if (configured === "false") {
    return false;
  }
  return process.env.NODE_ENV !== "development";
}

export function getRefreshCookieName(): string {
  return resolveSecureCookie() ? "__Host-rentflow_refresh" : "rentflow_refresh";
}

export const REFRESH_COOKIE_NAME = getRefreshCookieName();
export const ROLE_COOKIE_NAME = "rentflow_role";

export function parseRoles(value: string | undefined | null): string[] {
  if (!value) return [];
  return value
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}
