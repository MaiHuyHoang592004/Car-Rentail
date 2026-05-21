"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { useAuth, type AuthRole } from "@/features/auth/auth-context";

type RoleGuardProps = {
  requiredRoles: AuthRole[];
  children: ReactNode;
  fallback?: ReactNode;
};

export function RoleGuard({ requiredRoles, children, fallback }: RoleGuardProps) {
  const router = useRouter();
  const { status, roles } = useAuth();
  const allowed = requiredRoles.some((role) => roles.includes(role));

  useEffect(() => {
    if (status === "guest") {
      router.replace("/login");
      return;
    }
    if (status === "authenticated" && !allowed) {
      router.replace("/forbidden");
    }
  }, [status, allowed, router]);

  if (status === "loading") {
    return fallback ?? <FullPageLoading />;
  }
  if (status === "authenticated" && allowed) {
    return <>{children}</>;
  }
  return null;
}

function FullPageLoading() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center">
      <p className="text-sm text-muted-foreground">Đang tải...</p>
    </div>
  );
}
