import type { ReactNode } from "react";

type BookingSectionCardProps = {
  title: string;
  icon?: ReactNode;
  children: ReactNode;
  className?: string;
};

export function BookingSectionCard({ title, icon, children, className = "" }: BookingSectionCardProps) {
  return (
    <section className={`rounded-xl border border-border bg-card p-5 shadow-sm ${className}`}>
      <h2 className="flex items-center gap-2 text-lg font-bold text-foreground">
        {icon ? <span className="text-primary">{icon}</span> : null}
        {title}
      </h2>
      <div className="mt-4">{children}</div>
    </section>
  );
}
