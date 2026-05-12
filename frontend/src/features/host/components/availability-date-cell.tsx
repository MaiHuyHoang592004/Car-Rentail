import { cn } from "@/lib/utils";
import type { HostAvailabilityDayViewModel } from "@/features/host/types";

function getStatusColor(status: HostAvailabilityDayViewModel["status"]): string {
  switch (status) {
    case "FREE":
      return "border-emerald-200 bg-emerald-50 text-emerald-900";
    case "BLOCKED":
      return "border-orange-200 bg-orange-50 text-orange-900";
    case "HOLD":
      return "border-indigo-200 bg-indigo-50 text-indigo-900";
    case "BOOKED":
      return "border-zinc-300 bg-zinc-100 text-zinc-800";
    default:
      return "border-border bg-background text-foreground";
  }
}

type AvailabilityDateCellProps = {
  day: HostAvailabilityDayViewModel;
  selected: boolean;
  onToggle: (date: string) => void;
};

export function AvailabilityDateCell({ day, selected, onToggle }: AvailabilityDateCellProps) {
  const canToggle = day.status === "FREE" || day.status === "BLOCKED";

  return (
    <button
      type="button"
      disabled={!canToggle}
      onClick={() => onToggle(day.date)}
      className={cn(
        "rounded-lg border px-2 py-2 text-left text-xs transition-colors",
        getStatusColor(day.status),
        selected && "ring-2 ring-primary",
        !canToggle && "cursor-not-allowed opacity-70",
      )}
    >
      <p className="font-semibold">{day.date}</p>
      <p className="mt-1">{day.status}</p>
    </button>
  );
}
