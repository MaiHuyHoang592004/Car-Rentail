"use client";

import Link from "next/link";
import { Car, ListPlus } from "lucide-react";

import { StatusBadge } from "@/components/rentflow/status-badge";
import { getTransmissionLabel, getFuelTypeLabel, getVehicleStatusLabel } from "@/lib/display-labels";
import type { HostVehicleViewModel } from "@/features/host/types";

type VehicleRowProps = {
  vehicle: HostVehicleViewModel;
};

export function VehicleRow({ vehicle }: VehicleRowProps) {
  const canCreateListing = vehicle.status === "ACTIVE";
  const plateNumberDisplay = vehicle.plateNumber ?? "Khong doc duoc";
  const vinDisplay = vehicle.vin ?? "Khong doc duoc";

  return (
    <article className="rounded-[1.5rem] border border-border/70 bg-card p-5 shadow-[0_18px_40px_-28px_rgba(15,23,42,0.35)]">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        {/* Left: vehicle info */}
        <div className="min-w-0 flex-1 space-y-2">
          <div className="flex items-center gap-2">
            <Car className="h-4 w-4 shrink-0 text-muted-foreground" />
            <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              {vehicle.city}
            </p>
          </div>
          <h3 className="truncate text-lg font-bold text-foreground">
            {vehicle.make} {vehicle.model} ({vehicle.year})
          </h3>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
            <span>{vehicle.category}</span>
            <span>&bull;</span>
            <span>{getTransmissionLabel(vehicle.transmission)}</span>
            <span>&bull;</span>
            <span>{getFuelTypeLabel(vehicle.fuelType)}</span>
            <span>&bull;</span>
            <span>{vehicle.seats} cho</span>
          </div>
          <div className="text-xs text-muted-foreground">
            <span>Bien so: <strong className="text-foreground">{plateNumberDisplay}</strong></span>
            <span className="ml-3">VIN: <strong className="text-foreground">{vinDisplay}</strong></span>
          </div>
        </div>

        {/* Right: status + actions */}
        <div className="flex items-center gap-2 shrink-0">
          <StatusBadge status={vehicle.status} label={getVehicleStatusLabel(vehicle.status)} />
          {canCreateListing ? (
            <Link
              href="/host/listings/new"
            className="flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground transition-colors hover:bg-accent"
            >
              <ListPlus className="h-3.5 w-3.5" />
              Tao tin dang
            </Link>
          ) : null}
          <Link
            href={`/host/vehicles/${vehicle.id}`}
            className="rounded-full bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Quan ly
          </Link>
        </div>
      </div>
    </article>
  );
}
