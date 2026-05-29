import Link from "next/link";

import { StatusBadge } from "@/components/rentflow/status-badge";
import { getTransmissionLabel, getVehicleStatusLabel } from "@/lib/display-labels";
import { InfoBlock } from "@/components/rentflow/ui/info-block";
import type { HostVehicleViewModel } from "@/features/host/types";

type VehicleRowProps = {
  vehicle: HostVehicleViewModel;
};

export function VehicleRow({ vehicle }: VehicleRowProps) {
  return (
    <article className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{vehicle.city}</p>
          <h3 className="text-lg font-bold text-foreground">
            {vehicle.make} {vehicle.model} ({vehicle.year})
          </h3>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
            <InfoBlock label="Loại" value={vehicle.category} />
            <InfoBlock label="Hộp số" value={getTransmissionLabel(vehicle.transmission)} />
            <InfoBlock label="Nhiên liệu" value={vehicle.fuelType} />
            <InfoBlock label="Chỗ ngồi" value={vehicle.seats + " chỗ"} />
          </div>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
            <InfoBlock label="Biển số" value={vehicle.plateNumber} />
            <InfoBlock label="VIN" value={vehicle.vin} />
          </div>
        </div>

        <div className="flex items-center gap-2">
          <StatusBadge status={vehicle.status} label={getVehicleStatusLabel(vehicle.status)} />
          <Link
            href={`/host/vehicles/${vehicle.id}`}
            className="rounded-full bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Quản lý
          </Link>
        </div>
      </div>
    </article>
  );
}