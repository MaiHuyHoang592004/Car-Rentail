import { Lock } from "lucide-react";

import { formatMoney } from "@/lib/formatters";
import type { ListingDetailViewModel } from "@/features/listings/types";

type BookingPriceSummaryProps = {
  listing: ListingDetailViewModel;
  pickupDate: string;
  returnDate: string;
  selectedExtraIds: string[];
  onBook: () => void;
  isPending: boolean;
};

function calcDays(pickup: string, ret: string): number | null {
  if (!pickup || !ret) return null;
  const a = Date.parse(pickup);
  const b = Date.parse(ret);
  if (isNaN(a) || isNaN(b) || b <= a) return null;
  return Math.round((b - a) / 86400000);
}

export function BookingPriceSummary({
  listing,
  pickupDate,
  returnDate,
  selectedExtraIds,
  onBook,
  isPending,
}: BookingPriceSummaryProps) {
  const days = calcDays(pickupDate, returnDate);
  const extrasTotal = listing.extras
    .filter((e) => selectedExtraIds.includes(e.id))
    .reduce((sum, e) => sum + e.price, 0);
  const baseTotal = days != null ? listing.basePricePerDay * days : null;
  const grandTotal = days != null && baseTotal != null ? baseTotal + extrasTotal : null;
  const selectedExtras = listing.extras.filter((e) => selectedExtraIds.includes(e.id));
  const hasDates = Boolean(pickupDate && returnDate);

  return (
    <div className="sticky top-6 space-y-4">
      {/* Listing Card */}
      <div className="overflow-hidden rounded-xl border border-border bg-card shadow-sm">
        <div className="relative h-40 bg-muted">
          <img
            src={listing.coverImageUrl}
            alt={listing.title}
            className="h-full w-full object-cover"
          />
        </div>
        <div className="p-4 space-y-2">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{listing.city}</p>
          <h3 className="text-base font-bold text-foreground">{listing.title}</h3>
          <div className="flex items-center gap-1">
            <span className="text-lg font-bold text-foreground">{formatMoney(listing.basePricePerDay, listing.currency)}</span>
            <span className="text-sm text-muted-foreground">/ ngày</span>
          </div>
        </div>
      </div>

      {/* Price Breakdown Card */}
      <div className="rounded-xl border border-border bg-card p-5 shadow-sm space-y-3">
        {hasDates && days != null ? (
          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">{formatMoney(listing.basePricePerDay, listing.currency)} × {days} ngày</span>
              <span className="font-medium text-foreground">{formatMoney(baseTotal!, listing.currency)}</span>
            </div>

            {selectedExtras.length > 0 ? selectedExtras.map((extra) => (
              <div key={extra.id} className="flex justify-between text-sm">
                <span className="text-muted-foreground">{extra.name}</span>
                <span className="font-medium text-foreground">{formatMoney(extra.price, extra.currency)}</span>
              </div>
            )) : null}

            <div className="border-t border-border pt-2 flex justify-between font-bold text-foreground">
              <span>Tổng tạm tính</span>
              <span>{formatMoney(grandTotal!, listing.currency)}</span>
            </div>
          </div>
        ) : (
          <div className="rounded-lg border border-dashed border-border p-3 text-center text-sm text-muted-foreground">Chọn ngày để xem giá</div>
        )}

        <button
          type="button"
          onClick={onBook}
          disabled={isPending}
          className="w-full rounded-lg bg-primary py-3 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50 mt-2"
        >{isPending ? "Đang giữ xe..." : "Giữ xe trong 15 phút"}</button>

        <div className="flex items-center justify-center gap-1.5 text-xs text-muted-foreground">
          <Lock className="h-3.5 w-3.5" />
          Giữ xe trong 15 phút để hoàn tất thanh toán.
        </div>
      </div>
    </div>
  );
}
