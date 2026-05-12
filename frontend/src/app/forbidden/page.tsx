import { AppShell } from "@/components/rentflow/app-shell";
import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";

export default function ForbiddenPage() {
  return (
    <AppShell>
      <div className="mx-auto max-w-2xl space-y-4">
        <h1 className="text-3xl font-bold">Forbidden</h1>
        <ApiErrorPanel
          code="ACCESS_DENIED"
          message="You do not have permission to access this area."
          correlationId="demo-correlation-id"
        />
      </div>
    </AppShell>
  );
}
