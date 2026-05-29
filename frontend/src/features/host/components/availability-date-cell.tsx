"use client";

import { cn } from "@/lib/utils";
import type { HostAvailabilityDayViewModel } from "@/features/host/types";

function statusLabel(status: string): string {
  switch (status) {
    case "FREE": return "Trong";
    case "BLOCKED": return "Da chan";
    case "HOLD": return "Dang giu";
    case "BOOKED": return "Da dat";
    default: return status;
  }
}

function getStatusColor(status: string): string {
  switch (status) {
    case "FREE": return "border-emerald-200 bg-emerald-50 text-emerald-900";
    case "BLOCKED": return "border-orange-200 bg-orange-50 text-orange-900";
    case "HOLD": return "border-indigo-200 bg-indigo-50 text-indigo-900";
    case "BOOKED": return "border-zinc-300 bg-zinc-100 text-zinc-800";
    default: return "border-border bg-background text-foreground";
  }
}

type AvailabilityDateCellProps = {
  day: HostAvailabilityDayViewModel;
  selected: boolean;
  onToggle: (date: string) => void;
};

function formatDate(dateStr: string): { day: string; weekday: string } {
  const d = new Date(dateStr + "T00:00:00");
  return {
    day: d.toLocaleString("vi-VN", { day: "numeric", month: "short" }),
    weekday: d.toLocaleString("vi-VN", { weekday: "short" }),
  };
}

export function AvailabilityDateCell({ day, selected, onToggle }: AvailabilityDateCellProps) {
  const canToggle = day.status === "FREE" || day.status === "BLOCKED";
  const { day: dayLabel, weekday } = formatDate(day.date);

  return (
    <button
      type="button"
      disabled={!canToggle}
      onClick={() => onToggle(day.date)}
      className={cn(
        "rounded-lg border px-2 py-2 text-left text-xs transition-colors",
        getStatusColor(day.status),
        selected && "ring-2 ring-primary ring-offset-1",
        !canToggle && "cursor-not-allowed opacity-70",
      )}
    >
      <p className="text-[10px] text-muted-foreground">{weekday}</p>
      <p className="font-semibold">{dayLabel}</p>
      <p className="mt-0.5 text-[10px] opacity-80">{statusLabel(day.status)}</p>
    </button>
  );
}