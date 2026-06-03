"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AlertCircle, Car, ClipboardList, ListChecks } from "lucide-react";

import { FormError } from "@/components/rentflow/form-error";
import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { HostMetricStrip } from "@/features/host/components/host-metric-strip";
import { getHostVehiclesByStatus } from "@/features/host/vehicles/api";
import { getHostListings } from "@/features/host/listings/api";
import { getHostBookings } from "@/features/host/bookings/api";
import { getHostOverviewReport } from "@/features/host/reports/api";
import {
  getBookingStatusLabel,
  getVehicleStatusLabel,
  getListingStatusLabel,
} from "@/lib/display-labels";

const DASHBOARD_LIST_SIZE = 5;
const DASHBOARD_LISTING_SIZE = 100;
const DASHBOARD_VEHICLE_SIZE = 100;

export function HostDashboardPageView() {
  const today = new Date();
  const fromDate = new Date();
  fromDate.setDate(today.getDate() - 29);
  const from = fromDate.toISOString().split("T")[0];
  const to = today.toISOString().split("T")[0];

  const vehiclesQuery = useQuery({
    queryKey: ["host", "vehicles", "dashboard"],
    queryFn: () => getHostVehiclesByStatus("ALL", 0, DASHBOARD_VEHICLE_SIZE),
  });

  const listingsQuery = useQuery({
    queryKey: ["host", "listings", "dashboard"],
    queryFn: () => getHostListings(undefined, 0, DASHBOARD_LISTING_SIZE),
  });

  const pendingBookingsQuery = useQuery({
    queryKey: ["host", "bookings", "pending-dashboard"],
    queryFn: () =>
      getHostBookings({
        status: "PENDING_HOST_APPROVAL",
        page: 0,
        size: DASHBOARD_LIST_SIZE,
      }),
  });

  const overviewQuery = useQuery({
    queryKey: ["host", "reports", "overview", from, to],
    queryFn: () => getHostOverviewReport(from, to),
  });

  const vehicles = vehiclesQuery.data?.content ?? [];
  const listings = listingsQuery.data?.listings ?? [];
  const pendingBookings = pendingBookingsQuery.data?.content ?? [];

  const vehicleAttention = vehicles.filter((vehicle) =>
    ["MAINTENANCE", "SUSPENDED"].includes(vehicle.status),
  );
  const listingAttention = listings.filter((listing) =>
    ["PENDING_APPROVAL", "SUSPENDED"].includes(listing.status),
  );

  const canUseListingsFallback = !listingsQuery.isError;
  const canUseVehiclesFallback = !vehiclesQuery.isError;
  const metrics = {
    totalVehicles: canUseVehiclesFallback ? vehiclesQuery.data?.totalElements ?? vehicles.length : 0,
    activeListings: overviewQuery.data?.activeListings ?? 0,
    pendingApprovals:
      overviewQuery.data?.pendingApprovalListings
      ?? (canUseListingsFallback ? listings.filter((listing) => listing.status === "PENDING_APPROVAL").length : 0),
    blockedDates: overviewQuery.data?.blockedDays ?? 0,
  };

  return (
    <WorkspaceSidebar
      sidebar={<HostWorkspaceNav />}
      activePath="/host/dashboard"
    >
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Bang dieu khien Chu xe</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Tong quan dong xe, tin dang va cac thao tac dang cho xu ly.
          </p>
        </div>

        <HostMetricStrip metrics={metrics} />

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
            <Link
              href="/host/reports"
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:bg-accent"
            >
              Xem bao cao
            </Link>
          </div>
        </section>

        <div className="grid gap-4 lg:grid-cols-2">
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
            {vehiclesQuery.isLoading ? (
              <p className="mt-3 text-sm text-muted-foreground">Dang tai...</p>
            ) : vehiclesQuery.isError ? (
              <FormError>Khong tai duoc du lieu xe can xu ly.</FormError>
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
            {listingsQuery.isLoading ? (
              <p className="mt-3 text-sm text-muted-foreground">Dang tai...</p>
            ) : listingsQuery.isError ? (
              <FormError>Khong tai duoc du lieu tin dang can xu ly.</FormError>
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
              {pendingBookings.length > 0 && (
                <span className="ml-auto flex h-5 w-5 items-center justify-center rounded-full bg-amber-100 text-[10px] font-bold text-amber-800">
                  {pendingBookings.length}
                </span>
              )}
            </div>
            {pendingBookingsQuery.isLoading ? (
              <p className="mt-3 text-sm text-muted-foreground">Dang tai...</p>
            ) : pendingBookingsQuery.isError ? (
              <FormError>Khong tai duoc danh sach booking cho duyet.</FormError>
            ) : pendingBookings.length === 0 ? (
              <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                <AlertCircle className="h-4 w-4 text-emerald-600" />
                Khong co booking nao dang cho duyet.
              </div>
            ) : (
              <div className="mt-3 space-y-2">
                {pendingBookings.map((booking) => (
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

        {overviewQuery.isError ? (
          <FormError>Khong tai duoc bao cao tong quan. So lieu tong hop dang dung fallback an toan.</FormError>
        ) : null}
      </div>
    </WorkspaceSidebar>
  );
}
