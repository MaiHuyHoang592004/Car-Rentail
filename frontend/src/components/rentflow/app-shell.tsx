"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

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

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-20 border-b border-border bg-background/95 backdrop-blur">
        <div className="mx-auto flex h-16 w-full max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <Link href="/" className="font-heading text-xl font-bold text-foreground">
            RentFlow
          </Link>
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
