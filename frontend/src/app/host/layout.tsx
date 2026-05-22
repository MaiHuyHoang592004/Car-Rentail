import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import type { ReactNode } from "react";

import { ROLE_COOKIE_NAME, parseRoles } from "@/lib/session-cookie-shared";

export default async function HostLayout({ children }: { children: ReactNode }) {
  const cookieStore = await cookies();
  const roles = parseRoles(cookieStore.get(ROLE_COOKIE_NAME)?.value);

  if (roles.length === 0) {
    redirect("/login?next=/host");
  }
  if (!roles.includes("HOST")) {
    redirect("/forbidden");
  }

  return <>{children}</>;
}
