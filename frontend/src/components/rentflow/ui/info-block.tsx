import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

type InfoBlockProps = {
  label: string;
  value?: string | number | null | ReactNode;
  className?: string;
};

export function InfoBlock({ label, value, className }: InfoBlockProps) {
  if (value === undefined || value === null || value === "") return null;
  return (
    <div className={cn("flex flex-col gap-0.5", className)}>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="text-sm text-foreground">{value}</dd>
    </div>
  );
}