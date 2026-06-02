import { Suspense } from "react";

import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { VerifyEmailPageView } from "@/features/onboarding/verify-email-page-view";

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<PageSkeleton message="Đang tải xác minh email..." />}>
      <VerifyEmailPageView />
    </Suspense>
  );
}
