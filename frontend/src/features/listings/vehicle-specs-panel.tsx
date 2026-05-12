import type { ListingDetailViewModel } from "@/features/listings/types";

type VehicleSpecsPanelProps = {
  listing: ListingDetailViewModel;
};

export function VehicleSpecsPanel({ listing }: VehicleSpecsPanelProps) {
  const specs = [
    { label: "Make", value: listing.vehicle.make },
    { label: "Model", value: listing.vehicle.model },
    { label: "Year", value: String(listing.vehicle.year) },
    { label: "Category", value: listing.vehicle.category },
    { label: "Seats", value: String(listing.vehicle.seats) },
    { label: "Transmission", value: listing.vehicle.transmission },
    { label: "Fuel", value: listing.vehicle.fuelType },
    { label: "Daily KM limit", value: String(listing.dailyKmLimit) },
    { label: "Cancellation", value: listing.cancellationPolicy },
    { label: "Instant book", value: listing.instantBook ? "Yes" : "No" },
  ];

  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h2 className="text-xl font-bold text-foreground">Vehicle Specs</h2>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        {specs.map((spec) => (
          <div key={spec.label} className="rounded-lg border border-border bg-background px-3 py-2">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">{spec.label}</p>
            <p className="mt-1 text-sm font-semibold text-foreground">{spec.value}</p>
          </div>
        ))}
      </div>

      <h3 className="mt-5 text-sm font-bold uppercase tracking-wide text-muted-foreground">Extras</h3>
      <ul className="mt-2 space-y-2">
        {listing.extras.map((extra) => (
          <li
            key={extra.id}
            className="flex items-center justify-between rounded-lg border border-border bg-background px-3 py-2"
          >
            <span className="text-sm font-medium text-foreground">{extra.name}</span>
            <span className="text-sm font-semibold text-foreground">
              {extra.price.toLocaleString("en-US")} {extra.currency}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
