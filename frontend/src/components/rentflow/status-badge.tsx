import { cn } from "@/lib/utils";

type StatusKind =
  | "HELD"
  | "PENDING_HOST_APPROVAL"
  | "CONFIRMED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "REJECTED"
  | "ACTIVE"
  | "DRAFT"
  | "MAINTENANCE"
  | "ARCHIVED"
  | "PENDING_APPROVAL"
  | "PENDING"
  | "SUSPENDED"
  | "CANCELLED"
  | "EXPIRED"
  | "APPROVED"
  | "BLOCKED"
  | "HOLD"
  | "BOOKED"
  | "UNAVAILABLE"
  | "FREE";

const STATUS_STYLE: Record<StatusKind, string> = {
  HELD: "bg-indigo-100 text-indigo-800",
  PENDING_HOST_APPROVAL: "bg-amber-100 text-amber-800",
  CONFIRMED: "bg-cyan-100 text-cyan-800",
  IN_PROGRESS: "bg-violet-100 text-violet-800",
  COMPLETED: "bg-emerald-100 text-emerald-800",
  REJECTED: "bg-rose-100 text-rose-800",
  ACTIVE: "bg-emerald-100 text-emerald-800",
  DRAFT: "bg-slate-200 text-slate-800",
  MAINTENANCE: "bg-violet-100 text-violet-800",
  ARCHIVED: "bg-zinc-200 text-zinc-700",
  PENDING_APPROVAL: "bg-amber-100 text-amber-800",
  PENDING: "bg-amber-100 text-amber-800",
  SUSPENDED: "bg-rose-100 text-rose-800",
  CANCELLED: "bg-zinc-200 text-zinc-700",
  EXPIRED: "bg-zinc-200 text-zinc-700",
  APPROVED: "bg-emerald-100 text-emerald-800",
  BLOCKED: "bg-orange-100 text-orange-800",
  HOLD: "bg-indigo-100 text-indigo-800",
  BOOKED: "bg-zinc-300 text-zinc-800",
  UNAVAILABLE: "bg-zinc-200 text-zinc-700",
  FREE: "bg-emerald-100 text-emerald-800",
};

type StatusBadgeProps = {
  status: StatusKind | string;
  className?: string;
};

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const key = status.toUpperCase() as StatusKind;
  const style = STATUS_STYLE[key] ?? "bg-zinc-200 text-zinc-700";

  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold tracking-wide",
        style,
        className,
      )}
    >
      {status}
    </span>
  );
}
