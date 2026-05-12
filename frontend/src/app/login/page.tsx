import { LoginPageView } from "@/features/auth/login-page-view";
import type { GuestIntentRedirect } from "@/features/auth/types";

type LoginPageProps = {
  searchParams: Promise<{ next?: string | string[] }>;
};

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const query = await searchParams;
  const nextParam = query.next;
  const redirectIntent: GuestIntentRedirect = {
    nextPath: Array.isArray(nextParam) ? nextParam[0] || "/listings" : nextParam || "/listings",
  };

  return <LoginPageView redirectIntent={redirectIntent} />;
}
