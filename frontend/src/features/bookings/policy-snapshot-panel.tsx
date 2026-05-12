import type { BookingDetailViewModel } from "@/features/bookings/types";

type PolicySnapshotPanelProps = {
  policySnapshot: BookingDetailViewModel["policySnapshot"];
};

export function PolicySnapshotPanel({ policySnapshot }: PolicySnapshotPanelProps) {
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h2 className="text-lg font-bold text-foreground">Policy Snapshot</h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-3">
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Cancellation</p>
          <p className="mt-1 text-sm font-semibold text-foreground">
            {policySnapshot.cancellationPolicy}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Instant book</p>
          <p className="mt-1 text-sm font-semibold text-foreground">
            {policySnapshot.instantBook ? "Yes" : "No"}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Daily km limit</p>
          <p className="mt-1 text-sm font-semibold text-foreground">
            {policySnapshot.dailyKmLimit.toLocaleString("en-US")}
          </p>
        </div>
      </div>
    </section>
  );
}
