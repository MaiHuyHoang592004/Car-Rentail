"use client";

import { useEffect } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";

type ErrorPageProps = {
  error: Error & { digest?: string };
  reset: () => void;
};

export default function ErrorPage({ error, reset }: ErrorPageProps) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <AppShell>
      <div className="mx-auto max-w-2xl space-y-4">
        <h1 className="text-3xl font-bold">System Error</h1>
        <ApiErrorPanel
          code="INTERNAL_ERROR"
          message="An unexpected error occurred while rendering this page."
          correlationId={error.digest}
        />
        <button
          onClick={reset}
          className="rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90"
        >
          Retry
        </button>
      </div>
    </AppShell>
  );
}
