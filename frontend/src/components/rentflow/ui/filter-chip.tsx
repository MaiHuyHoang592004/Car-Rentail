import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export type FilterChipProps = {
  label: string;
  selected?: boolean;
  icon?: ReactNode;
  count?: number;
  onClick?: () => void;
  className?: string;
};

export function FilterChip({ label, selected = false, icon, count, onClick, className }: FilterChipProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "inline-flex shrink-0 items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors",
        selected
          ? "border-primary bg-primary text-primary-foreground"
          : "border-border bg-background text-muted-foreground hover:border-muted-foreground hover:text-foreground",
        className,
      )}
    >
      {icon}
      {label}
      {count !== undefined && (
        <span
          className={cn(
            "ml-0.5 rounded-full px-1.5 py-0.5 text-[10px] font-semibold",
            selected ? "bg-primary-foreground/20 text-primary-foreground" : "bg-muted text-muted-foreground",
          )}
        >
          {count}
        </span>
      )}
    </button>
  );
}