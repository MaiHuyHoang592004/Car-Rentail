import type { ReactNode } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";

type RoutePlaceholderProps = {
  title: string;
  description: string;
  activePath: string;
  children?: ReactNode;
};

export function RoutePlaceholder({
  title,
  description,
  activePath,
  children,
}: RoutePlaceholderProps) {
  return (
    <AppShell activePath={activePath}>
      <div className="space-y-6">
        <PageHeader title={title} description={description} />
        <section className="rounded-lg border border-border bg-card p-5 shadow-sm">
          <p className="text-sm text-muted-foreground">
            Static placeholder built from Phase 5 roadmap. API wiring comes in later phases.
          </p>
          {children ? <div className="mt-5">{children}</div> : null}
        </section>
      </div>
    </AppShell>
  );
}
