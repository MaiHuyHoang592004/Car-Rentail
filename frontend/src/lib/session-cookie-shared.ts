export const REFRESH_COOKIE_NAME = "rentflow_refresh";
export const ROLE_COOKIE_NAME = "rentflow_role";

export function parseRoles(value: string | undefined | null): string[] {
  if (!value) return [];
  return value
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}
