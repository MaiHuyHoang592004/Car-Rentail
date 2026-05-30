"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Car,
  ListChecks,
  ClipboardList,
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
  {
    href: "/host/bookings",
    label: "Bookings",
    icon: ClipboardList,
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
        "rf-section-card sticky top-24 flex flex-col overflow-hidden transition-all duration-200",
        collapsed ? "w-18" : "w-64",
      )}
    >
      <div className="flex h-16 items-center justify-between border-b border-border px-4">
        {!collapsed && (
          <span className="text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground">
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

      <nav className="flex-1 space-y-1 p-3" aria-label="Host workspace">
        {NAV_ITEMS.map((item) => {
          const active = isActive(pathname, item);
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-3 rounded-2xl px-3 py-3 text-sm font-medium transition-colors",
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
        <div className="space-y-2 border-t border-border p-3">
          <Link
            href="/host/vehicles/new"
            className="flex items-center gap-2 rounded-2xl bg-primary px-3 py-2.5 text-sm font-semibold text-primary-foreground hover:opacity-90"
          >
            <Plus className="h-4 w-4" />
            Them xe
          </Link>
          <Link
            href="/host/listings/new"
            className="flex items-center gap-2 rounded-2xl border border-border bg-background px-3 py-2.5 text-sm font-semibold text-foreground hover:bg-accent"
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
            className="flex h-10 w-10 items-center justify-center rounded-2xl bg-primary text-primary-foreground hover:opacity-90"
          >
            <Plus className="h-4 w-4" />
          </Link>
        </div>
      )}
    </aside>
  );
}
