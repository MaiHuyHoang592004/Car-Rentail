import type { ReactNode } from "react";

import { RoleGuard } from "@/features/auth/role-guard";

export default function HostLayout({ children }: { children: ReactNode }) {
  return <RoleGuard requiredRoles={["HOST"]}>{children}</RoleGuard>;
}
