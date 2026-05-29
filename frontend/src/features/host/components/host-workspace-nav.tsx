"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Car,
  ListChecks,
  CalendarDays,
  Plus,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";

const NAV_ITEMS = [
  {
    href: "/host/dashboard",
    label: "Tong quan",
    icon: LayoutDashboard,
    exact: true,
  },
  {
    href: "/host/vehicles",
    label: "Xe cua toi",
    icon: Car,
    exact: false,
  },
  {
    href: "/host/listings",
    label: "Tin dang",
    icon: ListChecks,
    exact: false,
  },
] as const;

type NavLink = (typeof NAV_ITEMS)[number];

function isActive(current: string, item: NavLink): boolean {
  if (item.exact) return current === item.href;
  return current === item.href || current.startsWith(item.href + "/");
}

export function HostWorkspaceNav() {
  const pathname = usePathname() ?? "";
  const [collapsed, setCollapsed] = useState(false);

  return (
    <aside
      className={cn(
        "flex flex-col border-r border-border bg-card transition-all duration-200",
        collapsed ? "w-16" : "w-56",
      )}
    >
      <div className="flex h-14 items-center justify-between border-b border-border px-3">
        {!collapsed && (
          <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
            Chu xe
          </span>
        )}
        <button
          type="button"
          onClick={() => setCollapsed((c) => !c)}
          aria-label={collapsed ? "Mo rong menu" : "Thu gon menu"}
          className="ml-auto flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
        >
          {collapsed ? (
            <ChevronRight className="h-4 w-4" />
          ) : (
            <ChevronLeft className="h-4 w-4" />
          )}
        </button>
      </div>

      <nav className="flex-1 space-y-1 p-2" aria-label="Host workspace">
        {NAV_ITEMS.map((item) => {
          const active = isActive(pathname, item);
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                active
                  ? "bg-primary text-primary-foreground"
                  : "text-muted-foreground hover:bg-accent hover:text-foreground",
                collapsed && "justify-center px-0",
              )}
              title={collapsed ? item.label : undefined}
            >
              <Icon className="h-5 w-5 shrink-0" />
              {!collapsed && <span>{item.label}</span>}
            </Link>
          );
        })}
      </nav>

      {!collapsed ? (
        <div className="border-t border-border p-3 space-y-2">
          <Link
            href="/host/vehicles/new"
            className="flex items-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90"
          >
            <Plus className="h-4 w-4" />
            Them xe
          </Link>
          <Link
            href="/host/listings/new"
            className="flex items-center gap-2 rounded-lg border border-border bg-background px-3 py-2 text-sm font-semibold text-foreground hover:bg-accent"
          >
            <Plus className="h-4 w-4" />
            Tao tin dang
          </Link>
        </div>
      ) : (
        <div className="border-t border-border p-2">
          <Link
            href="/host/vehicles/new"
            aria-label="Them xe"
            className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground hover:opacity-90"
          >
            <Plus className="h-4 w-4" />
          </Link>
        </div>
      )}
    </aside>
  );
}