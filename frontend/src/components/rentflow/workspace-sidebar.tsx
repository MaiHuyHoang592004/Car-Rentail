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
      <div className={cn("flex min-h-[calc(100vh-4rem)]", className)}>
        <div className="hidden w-56 shrink-0 lg:block">
          {sidebar}
        </div>
        <main className="flex-1 min-w-0 px-4 py-6 sm:px-6 lg:px-8">
          {children}
        </main>
      </div>
    </AppShell>
  );
}
