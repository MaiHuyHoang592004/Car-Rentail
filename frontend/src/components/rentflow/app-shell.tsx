"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";

import { UserMenu } from "@/components/rentflow/user-menu";
import { useAuth, type AuthRole } from "@/features/auth/auth-context";
import { cn } from "@/lib/utils";

type NavItem = {
  href: string;
  label: string;
  visibleTo: "public" | "authenticated" | AuthRole;
};

const NAV: NavItem[] = [
  { href: "/", label: "Trang chủ", visibleTo: "public" },
  { href: "/listings", label: "Tìm xe", visibleTo: "public" },
  { href: "/me/bookings", label: "Đơn của tôi", visibleTo: "authenticated" },
  { href: "/host/dashboard", label: "Host", visibleTo: "HOST" },
  { href: "/admin", label: "Admin", visibleTo: "ADMIN" },
];

type AppShellProps = {
  children: ReactNode;
  activePath?: string;
};

export function AppShell({ children, activePath }: AppShellProps) {
  const pathname = usePathname();
  const active = activePath ?? pathname ?? "/";
  const { status, hasRole } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);

  const visibleNav = NAV.filter((item) => {
    switch (item.visibleTo) {
      case "public":
        return true;
      case "authenticated":
        return status === "authenticated";
      default:
        return status === "authenticated" && hasRole(item.visibleTo);
    }
  });

  useEffect(() => {
    if (!mobileOpen) return;
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") setMobileOpen(false);
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [mobileOpen]);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-20 border-b border-border bg-background/95 backdrop-blur">
        <div className="mx-auto flex h-16 w-full max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <div className="flex items-center gap-2">
            <button
              type="button"
              aria-label="Mở menu"
              aria-expanded={mobileOpen}
              aria-controls="mobile-nav"
              onClick={() => setMobileOpen((open) => !open)}
              className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border bg-background text-foreground transition-colors hover:bg-accent md:hidden"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="h-5 w-5"
                aria-hidden="true"
              >
                <line x1="3" y1="6" x2="21" y2="6" />
                <line x1="3" y1="12" x2="21" y2="12" />
                <line x1="3" y1="18" x2="21" y2="18" />
              </svg>
            </button>
            <Link href="/" className="font-heading text-xl font-bold text-foreground">
              RentFlow
            </Link>
          </div>
          <nav className="hidden items-center gap-6 md:flex">
            {visibleNav.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "text-sm font-semibold text-muted-foreground transition-colors hover:text-foreground",
                  isActive(active, item.href) && "text-foreground",
                )}
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <UserMenu />
        </div>
      </header>

      {mobileOpen ? (
        <div className="fixed inset-0 z-30 md:hidden" role="dialog" aria-modal="true">
          <div
            className="absolute inset-0 bg-foreground/40"
            onClick={() => setMobileOpen(false)}
            aria-hidden="true"
          />
          <nav
            id="mobile-nav"
            aria-label="Điều hướng chính"
            className="absolute left-0 top-0 h-full w-72 max-w-[80vw] overflow-y-auto border-r border-border bg-background p-4 shadow-lg"
          >
            <div className="mb-4 flex items-center justify-between">
              <span className="font-heading text-lg font-bold text-foreground">RentFlow</span>
              <button
                type="button"
                aria-label="Đóng menu"
                onClick={() => setMobileOpen(false)}
                className="inline-flex h-8 w-8 items-center justify-center rounded-md text-foreground hover:bg-accent"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="h-5 w-5"
                  aria-hidden="true"
                >
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>
            <ul className="space-y-1">
              {visibleNav.map((item) => (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    onClick={() => setMobileOpen(false)}
                    className={cn(
                      "block rounded-lg px-3 py-2 text-sm font-semibold text-muted-foreground transition-colors hover:bg-accent hover:text-foreground",
                      isActive(active, item.href) && "bg-accent text-foreground",
                    )}
                  >
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      ) : null}

      <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">{children}</main>
    </div>
  );
}

function isActive(current: string, href: string): boolean {
  if (href === "/") {
    return current === "/";
  }
  return current === href || current.startsWith(`${href}/`);
}
