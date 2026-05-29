import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

const PILL_STYLES: Record<string, string> = {
  HELD: "bg-indigo-100 text-indigo-800",
  PENDING_HOST_APPROVAL: "bg-amber-100 text-amber-800",
  CONFIRMED: "bg-cyan-100 text-cyan-800",
  IN_PROGRESS: "bg-violet-100 text-violet-800",
  COMPLETED: "bg-emerald-100 text-emerald-800",
  CANCELLED: "bg-zinc-200 text-zinc-700",
  REJECTED: "bg-rose-100 text-rose-800",
  EXPIRED: "bg-zinc-200 text-zinc-700",
  ACTIVE: "bg-emerald-100 text-emerald-800",
  DRAFT: "bg-zinc-200 text-zinc-700",
  MAINTENANCE: "bg-amber-100 text-amber-800",
  ARCHIVED: "bg-zinc-200 text-zinc-700",
  PENDING_APPROVAL: "bg-amber-100 text-amber-800",
  PENDING: "bg-amber-100 text-amber-800",
  SUSPENDED: "bg-rose-100 text-rose-800",
  APPROVED: "bg-emerald-100 text-emerald-800",
  BLOCKED: "bg-orange-100 text-orange-800",
  HOLD: "bg-indigo-100 text-indigo-800",
  BOOKED: "bg-zinc-300 text-zinc-800",
  UNAVAILABLE: "bg-zinc-200 text-zinc-700",
  FREE: "bg-emerald-100 text-emerald-800",
};

export type StatusPillProps = {
  label: string;
  style?: string;
  className?: string;
  icon?: ReactNode;
};

export function StatusPill({ label, style, className, icon }: StatusPillProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold tracking-wide",
        style,
        className,
      )}
    >
      {icon}
      {label}
    </span>
  );
}

export type EnumStatusPillProps = {
  value: string;
  label: string;
  className?: string;
  icon?: ReactNode;
};

export function EnumStatusPill({ value, label, className, icon }: EnumStatusPillProps) {
  const bgClass = PILL_STYLES[value.toUpperCase()] ?? "bg-zinc-200 text-zinc-700";
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold tracking-wide",
        bgClass,
        className,
      )}
    >
      {icon}
      {label}
    </span>
  );
}