import { AppShell } from "@/components/rentflow/app-shell";

export default function NotFound() {
  return (
    <AppShell>
      <div className="mx-auto max-w-2xl rounded-lg border border-border bg-card p-8 text-center shadow-sm">
        <p className="text-sm uppercase tracking-wide text-muted-foreground">404 / 410</p>
        <h1 className="mt-3 text-3xl font-bold">Resource Not Found</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          This item is unavailable or no longer exists.
        </p>
      </div>
    </AppShell>
  );
}
