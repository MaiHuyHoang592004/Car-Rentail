import { getCancellationPolicyLabel } from "@/lib/display-labels";
import type { BookingDetailViewModel } from "@/features/bookings/types";

type PolicySnapshotPanelProps = {
  policySnapshot: BookingDetailViewModel["policySnapshot"];
};

export function PolicySnapshotPanel({ policySnapshot }: PolicySnapshotPanelProps) {
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h2 className="text-lg font-bold text-foreground">Chinh sach</h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-3">
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">
            Huy ban
          </p>
          <p className="mt-1 text-sm font-semibold text-foreground">
            {getCancellationPolicyLabel(policySnapshot.cancellationPolicy)}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">
            Dat ngay
          </p>
          <p className="mt-1 text-sm font-semibold text-foreground">
            {policySnapshot.instantBook ? "Co" : "Khong"}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">
            Gioi han km / ngay
          </p>
          <p className="mt-1 text-sm font-semibold text-foreground">
            {policySnapshot.dailyKmLimit.toLocaleString("vi-VN")}
          </p>
        </div>
      </div>
    </section>
  );
}
