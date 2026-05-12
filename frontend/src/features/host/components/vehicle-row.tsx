import Link from "next/link";

import { StatusBadge } from "@/components/rentflow/status-badge";
import type { HostVehicleViewModel } from "@/features/host/types";

type VehicleRowProps = {
  vehicle: HostVehicleViewModel;
};

export function VehicleRow({ vehicle }: VehicleRowProps) {
  return (
    <article className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            {vehicle.city}
          </p>
          <h3 className="text-lg font-bold text-foreground">
            {vehicle.make} {vehicle.model} ({vehicle.year})
          </h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {vehicle.category} • {vehicle.transmission} • {vehicle.fuelType} • {vehicle.seats} seats
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            Plate: {vehicle.plateNumber} • VIN: {vehicle.vin}
          </p>
        </div>

        <div className="flex items-center gap-2">
          <StatusBadge status={vehicle.status} />
          <Link
            href={`/host/vehicles/${vehicle.id}`}
            className="rounded-full bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Manage
          </Link>
        </div>
      </div>
    </article>
  );
}
