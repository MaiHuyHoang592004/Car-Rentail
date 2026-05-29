/**
 * Centralized formatting helpers.
 * Use these instead of raw toLocaleString or inline formatting logic.
 */

const VND_LOCALE = "vi-VN";

export function formatMoney(amount: number, currency = "VND"): string {
  return new Intl.NumberFormat(VND_LOCALE, {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
    minimumFractionDigits: 0,
  }).format(amount);
}

export function formatMoneyPlain(amount: number): string {
  return new Intl.NumberFormat(VND_LOCALE, {
    maximumFractionDigits: 0,
    minimumFractionDigits: 0,
  }).format(amount);
}

export function formatDate(isoDate: string, options?: Intl.DateTimeFormatOptions): string {
  const date = new Date(isoDate);
  return date.toLocaleDateString(VND_LOCALE, options ?? { day: "2-digit", month: "2-digit", year: "numeric" });
}

export function formatDateTime(isoDate: string): string {
  const date = new Date(isoDate);
  return date.toLocaleString(VND_LOCALE, {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatDateRange(startIso: string, endIso: string): string {
  const start = new Date(startIso);
  const end = new Date(endIso);
  const fmt: Intl.DateTimeFormatOptions = { day: "2-digit", month: "2-digit" };
  const startStr = start.toLocaleDateString(VND_LOCALE, fmt);
  const endStr = end.toLocaleDateString(VND_LOCALE, fmt);
  return startStr + " \u2192 " + endStr;
}

export function formatCount(n: number, singular: string, plural: string): string {
  return n + " " + (n === 1 ? singular : plural);
}

export function formatVehicleSpecs(parts: (string | number | undefined | null)[]): string {
  return parts.filter(Boolean).join(" \u00b7 ");
}