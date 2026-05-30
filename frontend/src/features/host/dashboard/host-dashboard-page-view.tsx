"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AlertCircle, Car, ClipboardList, ListChecks } from "lucide-react";
import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { HostMetricStrip } from "@/features/host/components/host-metric-strip";
import { getHostVehiclesByStatus } from "@/features/host/vehicles/api";
import { getHostListings } from "@/features/host/listings/api";
import { getHostBookings } from "@/features/host/bookings/api";
import {
  getBookingStatusLabel,
  getVehicleStatusLabel,
  getListingStatusLabel,
} from "@/lib/display-labels";

export function HostDashboardPageView() {
  const { data: vehicles = [], isLoading: loadingVehicles } = useQuery({
    queryKey: ["host", "vehicles"],
    queryFn: () => getHostVehiclesByStatus(),
  });

  const { data: listingsResult, isLoading: loadingListings } = useQuery({
    queryKey: ["host", "listings"],
    queryFn: () => getHostListings(undefined, 0, 100),
  });

  const { data: pendingBookings, isLoading: loadingBookings } = useQuery({
    queryKey: ["host", "bookings", "pending-dashboard"],
    queryFn: () =>
      getHostBookings({
        status: "PENDING_HOST_APPROVAL",
        page: 0,
        size: 5,
      }),
  });

  const vehicleAttention = vehicles.filter((v) =>
    ["MAINTENANCE", "SUSPENDED"].includes(v.status),
  );
  const listingAttention = (listingsResult?.listings ?? []).filter((l) =>
    ["PENDING_APPROVAL", "SUSPENDED"].includes(l.status),
  );

  const totalVehicles = vehicles.length;
  const totalListings = listingsResult?.listings.length ?? 0;

  const metrics = {
    totalVehicles,
    activeListings: totalListings,
    pendingApprovals: (listingsResult?.listings ?? []).filter(
      (l) => l.status === "PENDING_APPROVAL",
    ).length,
    blockedDates: 0,
  };

  return (
    <WorkspaceSidebar
      sidebar={<HostWorkspaceNav />}
      activePath="/host/dashboard"
    >
      <div className="space-y-6">
        {/* Page title */}
        <div>
          <h1 className="text-2xl font-bold text-foreground">Bang dieu khien Chu xe</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Tong quan dong xe, tin dang va cac thao tac dang cho xu ly.
          </p>
        </div>

        <HostMetricStrip metrics={metrics} />

        {/* Quick Actions */}
        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <h2 className="text-lg font-bold text-foreground">Thao tac nhanh</h2>
          <div className="mt-3 flex flex-wrap gap-2">
            <Link
              href="/host/vehicles/new"
              className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
            >
              Them xe
            </Link>
            <Link
              href="/host/listings/new"
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:bg-accent"
            >
              Tao tin dang
            </Link>
            <Link
              href="/host/listings"
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:bg-accent"
            >
              Cap nhat lich
            </Link>
            <Link
              href="/host/bookings"
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:bg-accent"
            >
              Duyet booking
            </Link>
          </div>
        </section>

        {/* Two-column: attention items */}
        <div className="grid gap-4 lg:grid-cols-2">
          {/* Vehicles needing attention */}
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <div className="flex items-center gap-2">
              <Car className="h-4 w-4 text-muted-foreground" />
              <h2 className="text-base font-bold text-foreground">Xe can xu ly</h2>
              {vehicleAttention.length > 0 && (
                <span className="ml-auto flex h-5 w-5 items-center justify-center rounded-full bg-amber-100 text-[10px] font-bold text-amber-800">
                  {vehicleAttention.length}
                </span>
              )}
            </div>
            {loadingVehicles ? (
              <p className="mt-3 text-sm text-muted-foreground">Dang tai...</p>
            ) : vehicleAttention.length === 0 ? (
              <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                <AlertCircle className="h-4 w-4 text-emerald-600" />
                Khong co xe nao can xu ly.
              </div>
            ) : (
              <div className="mt-3 space-y-2">
                {vehicleAttention.map((vehicle) => (
                  <Link
                    key={vehicle.id}
                    href={`/host/vehicles/${vehicle.id}`}
                    className="flex items-center justify-between rounded-lg border border-border bg-background px-3 py-2 text-sm transition-colors hover:border-primary/50 hover:bg-accent"
                  >
                    <div>
                      <p className="font-semibold text-foreground">
                        {vehicle.make} {vehicle.model} ({vehicle.year})
                      </p>
                      <p className="text-muted-foreground">
                        {getVehicleStatusLabel(vehicle.status)} &bull; {vehicle.city}
                      </p>
                    </div>
                    <span className="text-xs text-muted-foreground">Xem</span>
                  </Link>
                ))}
              </div>
            )}
          </section>

          {/* Listings needing attention */}
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <div className="flex items-center gap-2">
              <ListChecks className="h-4 w-4 text-muted-foreground" />
              <h2 className="text-base font-bold text-foreground">Tin dang can xu ly</h2>
              {listingAttention.length > 0 && (
                <span className="ml-auto flex h-5 w-5 items-center justify-center rounded-full bg-amber-100 text-[10px] font-bold text-amber-800">
                  {listingAttention.length}
                </span>
              )}
            </div>
            {loadingListings ? (
              <p className="mt-3 text-sm text-muted-foreground">Dang tai...</p>
            ) : listingAttention.length === 0 ? (
              <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                <AlertCircle className="h-4 w-4 text-emerald-600" />
                Khong co tin dang nao can xu ly.
              </div>
            ) : (
              <div className="mt-3 space-y-2">
                {listingAttention.map((listing) => (
                  <Link
                    key={listing.id}
                    href={`/host/listings/${listing.id}`}
                    className="flex items-center justify-between rounded-lg border border-border bg-background px-3 py-2 text-sm transition-colors hover:border-primary/50 hover:bg-accent"
                  >
                    <div>
                      <p className="font-semibold text-foreground">{listing.title}</p>
                      <p className="text-muted-foreground">
                        {getListingStatusLabel(listing.status)} &bull; {listing.city}
                      </p>
                    </div>
                    <span className="text-xs text-muted-foreground">Xem</span>
                  </Link>
                ))}
              </div>
            )}
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <div className="flex items-center gap-2">
              <ClipboardList className="h-4 w-4 text-muted-foreground" />
              <h2 className="text-base font-bold text-foreground">Booking cho duyet</h2>
              {(pendingBookings?.content.length ?? 0) > 0 && (
                <span className="ml-auto flex h-5 w-5 items-center justify-center rounded-full bg-amber-100 text-[10px] font-bold text-amber-800">
                  {pendingBookings?.content.length}
                </span>
              )}
            </div>
            {loadingBookings ? (
              <p className="mt-3 text-sm text-muted-foreground">Dang tai...</p>
            ) : (pendingBookings?.content.length ?? 0) === 0 ? (
              <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                <AlertCircle className="h-4 w-4 text-emerald-600" />
                Khong co booking nao dang cho duyet.
              </div>
            ) : (
              <div className="mt-3 space-y-2">
                {pendingBookings?.content.map((booking) => (
                  <Link
                    key={booking.id}
                    href={`/host/bookings/${booking.id}`}
                    className="flex items-center justify-between rounded-lg border border-border bg-background px-3 py-2 text-sm transition-colors hover:border-primary/50 hover:bg-accent"
                  >
                    <div>
                      <p className="font-semibold text-foreground">{booking.listingTitle}</p>
                      <p className="text-muted-foreground">
                        {getBookingStatusLabel(booking.status)} &bull; {booking.pickupDate}
                      </p>
                    </div>
                    <span className="text-xs text-muted-foreground">Xem</span>
                  </Link>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </WorkspaceSidebar>
  );
}
