import Link from "next/link";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

type NavItem = {
  href: string;
  label: string;
};

const NAV: NavItem[] = [
  { href: "/", label: "Home" },
  { href: "/listings", label: "Listings" },
  { href: "/me/bookings", label: "My Bookings" },
  { href: "/host/dashboard", label: "Host" },
  { href: "/admin", label: "Admin" },
];

type AppShellProps = {
  children: ReactNode;
  activePath?: string;
};

export function AppShell({ children, activePath }: AppShellProps) {
  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-20 border-b border-border bg-background/95 backdrop-blur">
        <div className="mx-auto flex h-16 w-full max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <Link href="/" className="font-heading text-xl font-bold text-foreground">
            RentFlow
          </Link>
          <nav className="hidden items-center gap-6 md:flex">
            {NAV.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "text-sm font-semibold text-muted-foreground transition-colors hover:text-foreground",
                  activePath === item.href && "text-foreground",
                )}
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="rounded-full bg-primary px-3 py-1 text-xs font-semibold text-primary-foreground">
            Phase 5
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">{children}</main>
    </div>
  );
}
