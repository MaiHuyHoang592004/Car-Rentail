import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export type MetricCardProps = {
  label: string;
  value: string | number;
  icon?: ReactNode;
  iconBg?: string;
  trend?: { value: number; positive?: boolean };
  className?: string;
};

export function MetricCard({ label, value, icon, iconBg = "bg-primary/10", trend, className }: MetricCardProps) {
  return (
    <div className={cn("flex flex-col gap-3 rounded-xl border border-border bg-card p-4 shadow-sm", className)}>
      <div className="flex items-start justify-between">
        <span className={cn("flex size-9 items-center justify-center rounded-lg", iconBg)}>
          {icon}
        </span>
        {trend && (
          <span
            className={cn(
              "inline-flex items-center gap-0.5 text-xs font-semibold",
              trend.positive ? "text-emerald-600" : "text-rose-600",
            )}
          >
            {trend.positive ? "+" : "-"} {Math.abs(trend.value)}%
          </span>
        )}
      </div>
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
        <p className="mt-0.5 font-heading text-3xl font-bold text-foreground">{value}</p>
      </div>
    </div>
  );
}