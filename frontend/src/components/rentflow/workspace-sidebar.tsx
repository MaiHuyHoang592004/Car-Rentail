"use client"

import { type ReactNode } from "react"
import { AppShell } from "@/components/rentflow/app-shell"
import { cn } from "@/lib/utils"

type WorkspaceSidebarProps = {
  children: ReactNode;
  sidebar: ReactNode;
  activePath?: string;
  className?: string;
}

export function WorkspaceSidebar({
  children,
  sidebar,
  activePath,
  className,
}: WorkspaceSidebarProps) {
  return (
    <AppShell activePath={activePath}>
      <div className={cn("flex min-h-[calc(100vh-4.5rem)] gap-6", className)}>
        <div className="hidden w-64 shrink-0 lg:block">
          {sidebar}
        </div>
        <main className="min-w-0 flex-1 pb-8">
          {children}
        </main>
      </div>
    </AppShell>
  );
}
