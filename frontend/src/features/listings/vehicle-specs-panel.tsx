import { Zap, ShieldCheck } from "lucide-react";

import { formatMoney } from "@/lib/formatters";
import {
  getCancellationPolicyLabel,
  getFuelTypeLabel,
  getTransmissionLabel,
} from "@/lib/display-labels";
import type { ListingDetailViewModel } from "@/features/listings/types";

type VehicleSpecsPanelProps = {
  listing: ListingDetailViewModel;
};

export function VehicleSpecsPanel({ listing }: VehicleSpecsPanelProps) {
  const specs = [
    { label: "Hãng", value: listing.vehicle.make },
    { label: "Mẫu xe", value: listing.vehicle.model },
    { label: "Năm sản xuất", value: String(listing.vehicle.year) },
    { label: "Phân loại", value: listing.vehicle.category },
    { label: "Số chỗ", value: `${listing.vehicle.seats} chỗ` },
    { label: "Hộp số", value: getTransmissionLabel(listing.vehicle.transmission) },
    { label: "Nhiên liệu", value: getFuelTypeLabel(listing.vehicle.fuelType) },
    { label: "Giới hạn KM/ngày", value: `${listing.dailyKmLimit.toLocaleString("vi-VN")} km` },
  ];

  const policy = getCancellationPolicyLabel(listing.cancellationPolicy);

  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h2 className="text-lg font-bold text-foreground">Thông số xe</h2>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        {specs.map((spec) => (
          <div
            key={spec.label}
            className="rounded-lg border border-border bg-background px-3 py-2.5"
          >
            <p className="text-xs uppercase tracking-wide text-muted-foreground">{spec.label}</p>
            <p className="mt-1 text-sm font-semibold text-foreground">{spec.value}</p>
          </div>
        ))}
      </div>

      <div className="mt-4 flex items-center gap-3 rounded-lg border border-border bg-background px-3 py-2.5">
        <ShieldCheck className="h-5 w-5 shrink-0 text-primary" />
        <div className="flex-1 min-w-0">
          <p className="text-xs text-muted-foreground">Chính sách hủy</p>
          <p className="text-sm font-semibold text-foreground">{policy}</p>
        </div>
        {listing.instantBook ? (
          <span className="flex shrink-0 items-center gap-1 rounded-full bg-green-50 px-2.5 py-1 text-xs font-semibold text-green-700">
            <Zap className="h-3 w-3 fill-green-600 text-green-600" />
            Đặt ngay
          </span>
        ) : null}
      </div>

      {listing.extras.length > 0 ? (
        <>
          <h3 className="mt-5 text-sm font-bold uppercase tracking-wide text-muted-foreground">
            Phụ kiện kèm theo
          </h3>
          <ul className="mt-2 space-y-2">
            {listing.extras.map((extra) => (
              <li
                key={extra.id}
                className="flex items-center justify-between rounded-lg border border-border bg-background px-3 py-2.5"
              >
                <span className="text-sm font-medium text-foreground">{extra.name}</span>
                <span className="text-sm font-semibold text-foreground">
                  {formatMoney(extra.price, extra.currency)}
                </span>
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </section>
  );
}
