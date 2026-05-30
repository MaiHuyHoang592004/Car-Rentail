"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronDown, Info, MapPin } from "lucide-react";

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
  const rentalTotal = days != null ? listing.basePricePerDay * days : 0;
  const serviceFee = days != null ? Math.round(rentalTotal * 0.05) : 0;
  const insuranceFee = days != null ? Math.max(0, days * 75000) : 0;
  const total = days != null ? rentalTotal + serviceFee + insuranceFee : null;
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
    <section className="rounded-2xl border border-border bg-white p-6 shadow-lg">
      <div className="flex items-baseline justify-between">
        <span className="text-2xl font-semibold text-primary">
          {formatMoney(listing.basePricePerDay, listing.currency)}
        </span>
        <span className="text-sm text-muted-foreground">/ngày</span>
      </div>

      <div className="mt-5 space-y-4">
        <div className="grid grid-cols-2 overflow-hidden rounded-lg border border-border">
          <DateBox
            label="Nhận xe"
            value={pickup}
            onChange={handlePickupChange}
            min={new Date().toISOString().split("T")[0]}
            className="border-r border-border"
          />
          <DateBox
            label="Trả xe"
            value={ret}
            onChange={handleRetChange}
            min={pickup || new Date().toISOString().split("T")[0]}
          />
        </div>

        <div className="flex items-center justify-between rounded-lg border border-border p-4">
          <div>
            <p className="text-xs font-semibold uppercase text-muted-foreground">Địa điểm nhận xe</p>
            <p className="mt-1 text-sm font-semibold text-foreground">Nhận tại vị trí của xe</p>
          </div>
          <ChevronDown className="h-5 w-5 text-muted-foreground" />
        </div>

        {dateRangeInvalid ? (
          <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
            Ngày trả xe phải sau ngày nhận xe.
          </p>
        ) : null}
      </div>

      <div className="mt-5 space-y-3 border-t border-border pt-5">
        {days != null && total != null ? (
          <>
            <PriceRow label={`Đơn giá thuê (${days} ngày)`} value={formatMoney(rentalTotal, listing.currency)} />
            <PriceRow label="Phí dịch vụ" value={formatMoney(serviceFee, listing.currency)} />
            <PriceRow label="Bảo hiểm chuyến đi" value={formatMoney(insuranceFee, listing.currency)} />
            <div className="flex justify-between pt-3 text-xl font-semibold text-foreground">
              <span>Tổng cộng</span>
              <span className="text-primary">{formatMoney(total, listing.currency)}</span>
            </div>
          </>
        ) : (
          <p className="rounded-lg border border-dashed border-border p-4 text-center text-xs text-muted-foreground">
            Chọn ngày để xem giá ước tính.
          </p>
        )}
      </div>

      <button
        type="button"
        onClick={handleBooking}
        disabled={!canBook}
        className="mt-5 flex w-full items-center justify-center rounded-xl bg-primary py-3 text-sm font-semibold text-primary-foreground shadow-md transition-transform active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50"
      >
        Đặt xe ngay
      </button>
      <p className="mt-2 text-center text-xs text-muted-foreground">
        Bạn sẽ không bị trừ tiền ngay lúc này
      </p>

      <div className="mt-5 flex items-start gap-2 rounded-lg border border-border bg-muted/40 p-3">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
        <div className="min-w-0">
          <p className="text-xs font-semibold text-foreground">Chính sách hủy: {policy}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">
            Hủy miễn phí trước 24 giờ so với thời điểm nhận xe.
          </p>
        </div>
      </div>

      <div className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
        <MapPin className="h-3.5 w-3.5 shrink-0" />
        <span className="truncate">{listing.address}</span>
      </div>
    </section>
  );
}

function DateBox({
  label,
  value,
  min,
  onChange,
  className,
}: {
  label: string;
  value: string;
  min: string;
  onChange: (value: string) => void;
  className?: string;
}) {
  return (
    <label className={`block cursor-pointer p-4 transition-colors hover:bg-muted ${className ?? ""}`}>
      <span className="text-xs font-semibold uppercase text-muted-foreground">{label}</span>
      <input
        type="date"
        value={value}
        min={min}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 block w-full border-0 bg-transparent p-0 text-sm font-semibold text-foreground outline-none focus:ring-0"
      />
      <span className="mt-1 block text-xs text-muted-foreground">09:00</span>
    </label>
  );
}

function PriceRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between text-sm text-muted-foreground">
      <span>{label}</span>
      <span>{value}</span>
    </div>
  );
}
