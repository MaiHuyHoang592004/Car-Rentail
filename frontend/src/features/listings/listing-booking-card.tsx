
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { CalendarDays, MapPin, Info } from "lucide-react";

import { formatMoney } from "@/lib/formatters";
import { getCancellationPolicyLabel } from "@/lib/display-labels";
import type { ListingDetailViewModel } from "@/features/listings/types";

type ListingBookingCardProps = {
  listing: ListingDetailViewModel;
};

function calcDays(pickup: string, ret: string): number | null {
  if (!pickup || !ret) return null;
  const a = Date.parse(pickup);
  const b = Date.parse(ret);
  if (isNaN(a) || isNaN(b) || b <= a) return null;
  return Math.round((b - a) / 86400000);
}

export function ListingBookingCard({ listing }: ListingBookingCardProps) {
  const router = useRouter();
  const [pickup, setPickup] = useState("");
  const [ret, setRet] = useState("");
  const [dateError, setDateError] = useState(false);

  const days = calcDays(pickup, ret);
  const dateRangeInvalid = dateError && pickup && ret && ret <= pickup;
  const total = days != null ? listing.basePricePerDay * days : null;
  const policy = getCancellationPolicyLabel(listing.cancellationPolicy);

  function handlePickupChange(value: string) {
    setPickup(value);
    if (ret && value && ret <= value) setDateError(true);
    else setDateError(false);
  }

  function handleRetChange(value: string) {
    setRet(value);
    if (pickup && value && value <= pickup) setDateError(true);
    else setDateError(false);
  }

  function handleBooking() {
    if (!pickup || !ret) return;
    if (dateRangeInvalid) return;
    router.push(`/listings/${listing.id}/book?pickup=${pickup}&ret=${ret}`);
  }

  const canBook = pickup && ret && !dateRangeInvalid;
  return (
    <div className="sticky top-6 rounded-xl border border-border bg-card p-5 shadow-sm">
      <div className="mb-4 flex items-baseline gap-1">
        <span className="text-2xl font-bold text-foreground">
          {formatMoney(listing.basePricePerDay, listing.currency)}
        </span>
        <span className="text-sm text-muted-foreground">/ ngày</span>
      </div>

      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Ngày nhận xe</label>
            <div className="relative">
              <CalendarDays className="absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground pointer-events-none" />
              <input
                type="date"
                value={pickup}
                onChange={(e) => handlePickupChange(e.target.value)}
                min={new Date().toISOString().split("T")[0]}
                className="h-11 w-full rounded-lg border border-input bg-background pl-8 pr-2 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
              />
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Ngày trả xe</label>
            <div className="relative">
              <CalendarDays className="absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground pointer-events-none" />
              <input
                type="date"
                value={ret}
                onChange={(e) => handleRetChange(e.target.value)}
                min={pickup || new Date().toISOString().split("T")[0]}
                className="h-11 w-full rounded-lg border border-input bg-background pl-8 pr-2 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
              />
            </div>
          </div>
        </div>

        {dateRangeInvalid ? (
          <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">Ngày trả xe phải sau ngày nhận xe.</p>
        ) : null}

        {days != null && total != null ? (
          <div className="rounded-lg border border-border bg-muted/50 p-3 space-y-1.5">
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">
                {formatMoney(listing.basePricePerDay, listing.currency)} × {days} ngày
              </span>
              <span className="font-medium text-foreground">{formatMoney(total, listing.currency)}</span>
            </div>
            <p className="text-xs text-muted-foreground">Tổng ước tính — chưa bao gồm phí bảo hiểm và dịch vụ.</p>
          </div>
        ) : (
          <p className="rounded-lg border border-dashed border-border p-3 text-center text-xs text-muted-foreground">Chọn ngày để xem giá ước tính.</p>
        )}
      </div>

      <button
        type="button"
        onClick={handleBooking}
        disabled={!canBook}
        className="mt-4 w-full rounded-lg bg-primary py-3 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
      >Đặt xe</button>

      <p className="mt-2 text-center text-xs text-muted-foreground">Bạn sẽ được chuyển đến trang đăng nhập nếu chưa có tài khoản.</p>

      <div className="mt-4 flex items-start gap-2 rounded-lg border border-border bg-muted/30 p-3">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
        <div className="min-w-0">
          <p className="text-xs font-semibold text-foreground">Chính sách hủy: {policy}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">Hủy miễn phí trước 24 giờ so với thời điểm nhận xe.</p>
        </div>
      </div>

      <div className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
        <MapPin className="h-3.5 w-3.5 shrink-0" />
        <span className="truncate">{listing.address}</span>
      </div>
    </div>
  );
}
