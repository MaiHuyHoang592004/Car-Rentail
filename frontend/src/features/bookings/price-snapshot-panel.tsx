import type { BookingDetailViewModel } from "@/features/bookings/types";

type PriceSnapshotPanelProps = {
  priceSnapshot: BookingDetailViewModel["priceSnapshot"];
};

export function PriceSnapshotPanel({ priceSnapshot }: PriceSnapshotPanelProps) {
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h2 className="text-lg font-bold text-foreground">Price Snapshot</h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Rental days</p>
          <p className="mt-1 text-sm font-semibold text-foreground">{priceSnapshot.rentalDays}</p>
        </div>
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Base price/day</p>
          <p className="mt-1 text-sm font-semibold text-foreground">
            {priceSnapshot.basePricePerDay.toLocaleString("en-US")} {priceSnapshot.currency}
          </p>
        </div>
      </div>

      <div className="mt-3 rounded-lg border border-border bg-background p-3">
        <div className="space-y-1 text-sm text-foreground">
          <p className="flex items-center justify-between">
            <span>Base amount</span>
            <span className="font-semibold">
              {priceSnapshot.baseAmount.toLocaleString("en-US")} {priceSnapshot.currency}
            </span>
          </p>
          <p className="flex items-center justify-between">
            <span>Extras</span>
            <span className="font-semibold">
              {priceSnapshot.extraAmount.toLocaleString("en-US")} {priceSnapshot.currency}
            </span>
          </p>
          <p className="mt-2 flex items-center justify-between border-t border-border pt-2 text-base font-bold">
            <span>Total</span>
            <span>
              {priceSnapshot.totalAmount.toLocaleString("en-US")} {priceSnapshot.currency}
            </span>
          </p>
        </div>
      </div>

      <div className="mt-3 space-y-2">
        {priceSnapshot.extras.length === 0 ? (
          <p className="text-sm text-muted-foreground">No extras selected.</p>
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
                {extra.totalPrice.toLocaleString("en-US")} {extra.currency}
              </span>
            </div>
          ))
        )}
      </div>
    </section>
  );
}
