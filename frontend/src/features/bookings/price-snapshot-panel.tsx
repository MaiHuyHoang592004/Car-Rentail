import { formatMoney } from "@/lib/formatters";
import type { BookingDetailViewModel } from "@/features/bookings/types";

type PriceSnapshotPanelProps = {
  priceSnapshot: BookingDetailViewModel["priceSnapshot"];
};

export function PriceSnapshotPanel({ priceSnapshot }: PriceSnapshotPanelProps) {
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h2 className="text-lg font-bold text-foreground">Chi tiet gia</h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">
            So ngay thue
          </p>
          <p className="mt-1 text-sm font-semibold text-foreground">
            {priceSnapshot.rentalDays}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">
            Gia / ngay
          </p>
          <p className="mt-1 text-sm font-semibold text-foreground">
            {formatMoney(priceSnapshot.basePricePerDay, priceSnapshot.currency)}
          </p>
        </div>
      </div>

      <div className="mt-3 rounded-lg border border-border bg-background p-3">
        <div className="space-y-1 text-sm text-foreground">
          <p className="flex items-center justify-between">
            <span>Phi co ban</span>
            <span className="font-semibold">
              {formatMoney(priceSnapshot.baseAmount, priceSnapshot.currency)}
            </span>
          </p>
          <p className="flex items-center justify-between">
            <span>Dich vu them</span>
            <span className="font-semibold">
              {formatMoney(priceSnapshot.extraAmount, priceSnapshot.currency)}
            </span>
          </p>
          <p className="mt-2 flex items-center justify-between border-t border-border pt-2 text-base font-bold">
            <span>Tong cong</span>
            <span>
              {formatMoney(priceSnapshot.totalAmount, priceSnapshot.currency)}
            </span>
          </p>
        </div>
      </div>

      <div className="mt-3 space-y-2">
        {priceSnapshot.extras.length === 0 ? (
          <p className="text-sm text-muted-foreground">Khong co dich vu them.</p>
        ) : (
          priceSnapshot.extras.map((extra) => (
            <div
              key={extra.id}
              className="flex items-center justify-between rounded-lg border border-border bg-background px-3 py-2 text-sm"
            >
              <span className="font-medium text-foreground">
                {extra.name} x{extra.quantity}
              </span>
              <span className="font-semibold text-foreground">
                {formatMoney(extra.totalPrice, extra.currency)}
              </span>
            </div>
          ))
        )}
      </div>
    </section>
  );
}
