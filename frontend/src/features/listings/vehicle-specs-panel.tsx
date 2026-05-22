import type { ListingDetailViewModel } from "@/features/listings/types";

type VehicleSpecsPanelProps = {
  listing: ListingDetailViewModel;
};

export function VehicleSpecsPanel({ listing }: VehicleSpecsPanelProps) {
  const specs = [
    { label: "Hãng", value: listing.vehicle.make },
    { label: "Mẫu", value: listing.vehicle.model },
    { label: "Năm", value: String(listing.vehicle.year) },
    { label: "Phân loại", value: listing.vehicle.category },
    { label: "Số chỗ", value: String(listing.vehicle.seats) },
    { label: "Hộp số", value: listing.vehicle.transmission },
    { label: "Nhiên liệu", value: listing.vehicle.fuelType },
    { label: "Hạn mức KM/ngày", value: String(listing.dailyKmLimit) },
    { label: "Chính sách huỷ", value: listing.cancellationPolicy },
    { label: "Đặt ngay", value: listing.instantBook ? "Có" : "Không" },
  ];

  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h2 className="text-xl font-bold text-foreground">Thông số xe</h2>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        {specs.map((spec) => (
          <div key={spec.label} className="rounded-lg border border-border bg-background px-3 py-2">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">{spec.label}</p>
            <p className="mt-1 text-sm font-semibold text-foreground">{spec.value}</p>
          </div>
        ))}
      </div>

      <h3 className="mt-5 text-sm font-bold uppercase tracking-wide text-muted-foreground">Phụ kiện kèm theo</h3>
      <ul className="mt-2 space-y-2">
        {listing.extras.map((extra) => (
          <li
            key={extra.id}
            className="flex items-center justify-between rounded-lg border border-border bg-background px-3 py-2"
          >
            <span className="text-sm font-medium text-foreground">{extra.name}</span>
            <span className="text-sm font-semibold text-foreground">
              {extra.price.toLocaleString("vi-VN")} {extra.currency}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
