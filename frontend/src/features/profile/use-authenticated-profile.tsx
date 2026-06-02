"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";

import { EmptyState } from "@/components/rentflow/empty-state";
import { useAuth } from "@/features/auth/auth-context";
import { getProfile } from "@/features/profile/api";

export function useAuthenticatedProfile() {
  const auth = useAuth();
  const pathname = usePathname() ?? "/me/profile";
  const profileQuery = useQuery({
    queryKey: ["profile"],
    queryFn: getProfile,
    enabled: auth.status === "authenticated",
  });

  return {
    auth,
    profileQuery,
    isLoading: auth.status === "loading" || profileQuery.isLoading,
    isGuest: auth.status === "guest",
    loginHref: `/login?next=${encodeURIComponent(pathname)}`,
  };
}

export function SessionExpiredState({ loginHref }: { loginHref: string }) {
  return (
    <EmptyState
      title="Phiên đăng nhập không còn hợp lệ"
      description="Vui lòng đăng nhập lại để tiếp tục xác minh tài khoản."
      action={
        <Link
          href={loginHref}
          className="rounded-full bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-blue-700"
        >
          Đăng nhập lại
        </Link>
      }
    />
  );
}
