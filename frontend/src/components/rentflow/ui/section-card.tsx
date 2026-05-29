import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

type SectionCardProps = {
  title?: string;
  icon?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
};

export function SectionCard({ title, icon, action, children, className }: SectionCardProps) {
  return (
    <section className={cn("rounded-xl border border-border bg-card shadow-sm", className)}>
      {title !== undefined && (
        <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-3">
          <div className="flex items-center gap-2">
            {icon && <span className="text-muted-foreground">{icon}</span>}
            <h2 className="font-heading text-sm font-semibold text-foreground">{title}</h2>
          </div>
          {action && <div>{action}</div>}
        </div>
      )}
      <div className="p-4">{children}</div>
    </section>
  );
}