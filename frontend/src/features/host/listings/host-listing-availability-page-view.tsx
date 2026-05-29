"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { ArrowLeft, Ban, CheckCircle } from "lucide-react";

import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { AvailabilityDateCell } from "@/features/host/components/availability-date-cell";
import {
  blockAvailabilityDates,
  getHostAvailabilityByListingId,
  unblockAvailabilityDates,
} from "@/features/host/availability/api";
import { getHostListingById } from "@/features/host/listings/api";
import type { HostAvailabilityStatus } from "@/features/host/types";

type HostListingAvailabilityPageViewProps = {
  listingId: string;
};

function buildDateRange(): { from: string; to: string } {
  const today = new Date();
  const from = today.toISOString().split("T")[0];
  const future = new Date(today);
  future.setDate(future.getDate() + 90);
  const to = future.toISOString().split("T")[0];
  return { from, to };
}

type DayGroup = { label: string; days: { date: string; status: HostAvailabilityStatus; bookingId?: string; expiresAt?: string }[] };

function groupByMonth(days: { date: string; status: HostAvailabilityStatus; bookingId?: string; expiresAt?: string }[]): DayGroup[] {
  const groups: Map<string, DayGroup> = new Map();
  for (const day of days) {
    const d = new Date(day.date + "T00:00:00");
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    const label = d.toLocaleString("vi-VN", { month: "long", year: "numeric" });
    if (!groups.has(key)) {
      groups.set(key, { label: label.charAt(0).toUpperCase() + label.slice(1), days: [] });
    }
    groups.get(key)!.days.push(day);
  }
  return Array.from(groups.values());
}

export function HostListingAvailabilityPageView({ listingId }: HostListingAvailabilityPageViewProps) {
  const queryClient = useQueryClient();
  const { from, to } = buildDateRange();

  const { data: listing, isLoading: loadingListing } = useQuery({
    queryKey: ["host", "listings", listingId],
    queryFn: () => getHostListingById(listingId),
  });

  const { data: days = [], isLoading: loadingDays } = useQuery({
    queryKey: ["host", "listings", listingId, "availability", from, to],
    queryFn: () => getHostAvailabilityByListingId(listingId, from, to),
    enabled: !!listing,
  });

  const [selectedDates, setSelectedDates] = useState<string[]>([]);
  const [banner, setBanner] = useState<string>("");

  const { mutate: doBlock, isPending: blocking } = useMutation({
    mutationFn: (dates: string[]) => blockAvailabilityDates(listingId, dates),
    onSuccess: (count) => {
      setSelectedDates([]);
      setBanner(`Da chan ${count} ngay thanh cong.`);
      queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId, "availability"] });
    },
    onError: (err: Error) => {
      toast.error(err.message ?? "Loi khi chan ngay.");
    },
  });

  const { mutate: doUnblock, isPending: unblocking } = useMutation({
    mutationFn: (dates: string[]) => unblockAvailabilityDates(listingId, dates),
    onSuccess: (count) => {
      setSelectedDates([]);
      setBanner(`Da mo ${count} ngay thanh cong.`);
      queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId, "availability"] });
    },
    onError: (err: Error) => {
      toast.error(err.message ?? "Loi khi mo ngay.");
    },
  });

  if (loadingListing) {
    return (
      <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/listings">
        <div className="flex items-center justify-center p-20">
          <p className="text-sm text-muted-foreground">Dang tai...</p>
        </div>
      </WorkspaceSidebar>
    );
  }

  if (!listing) {
    return (
      <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-2xl font-bold text-foreground">Khong tim thay tin dang</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Tin dang nay khong ton tai hoac ban khong co quyen truy cap.
          </p>
          <Link
            href="/host/listings"
            className="mt-4 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Quay lai danh sach
          </Link>
        </section>
      </WorkspaceSidebar>
    );
  }

  const grouped = groupByMonth(days);

  function toggleDate(date: string) {
    setSelectedDates((prev) =>
      prev.includes(date) ? prev.filter((v) => v !== date) : [...prev, date],
    );
    setBanner("");
  }

  const canBlock = selectedDates.filter((d) => {
    const day = days.find((x) => x.date === d);
    return day && (day.status === "FREE" || day.status === "BLOCKED");
  });

  return (
    <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/listings">
      <div className="space-y-5">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Link
              href={`/host/listings/${listingId}`}
              className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <ArrowLeft className="h-4 w-4" />
              Quay lai
            </Link>
            <span className="text-muted-foreground">|</span>
            <h1 className="text-xl font-bold text-foreground">Lich xe: {listing.title}</h1>
          </div>
        </div>

        {/* Banner */}
        {banner ? (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            {banner}
          </section>
        ) : null}

        {/* Legend */}
        <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-1.5">
              <div className="h-3 w-3 rounded-full bg-emerald-400" />
              <span className="text-xs text-muted-foreground">Trong</span>
            </div>
            <div className="flex items-center gap-1.5">
              <div className="h-3 w-3 rounded-full bg-orange-400" />
              <span className="text-xs text-muted-foreground">Da chan</span>
            </div>
            <div className="flex items-center gap-1.5">
              <div className="h-3 w-3 rounded-full bg-indigo-400" />
              <span className="text-xs text-muted-foreground">Dang giu</span>
            </div>
            <div className="flex items-center gap-1.5">
              <div className="h-3 w-3 rounded-full bg-zinc-400" />
              <span className="text-xs text-muted-foreground">Da dat</span>
            </div>
          </div>

          {/* Selection info */}
          {selectedDates.length > 0 ? (
            <p className="mt-2 text-sm text-foreground">
              <strong>{selectedDates.length}</strong> ngay da chon
            </p>
          ) : (
            <p className="mt-2 text-sm text-muted-foreground">Nhan vao ngay de chon / bo chon.</p>
          )}

          {/* Month groups */}
          {loadingDays ? (
            <p className="mt-4 text-sm text-muted-foreground">Dang tai lich...</p>
          ) : days.length === 0 ? (
            <p className="mt-4 text-sm text-muted-foreground">Khong co du lieu lich trong khoang nay.</p>
          ) : (
            <div className="mt-4 space-y-6">
              {grouped.map((group) => (
                <div key={group.label}>
                  <h3 className="text-sm font-bold text-foreground mb-2">{group.label}</h3>
                  <div className="grid gap-2 sm:grid-cols-3 lg:grid-cols-5 xl:grid-cols-7">
                    {group.days.map((day) => (
                      <AvailabilityDateCell
                        key={day.date}
                        day={day}
                        selected={selectedDates.includes(day.date)}
                        onToggle={toggleDate}
                      />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Action buttons */}
          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => doBlock(selectedDates)}
              disabled={selectedDates.length === 0 || blocking}
              className="flex items-center gap-2 rounded-full bg-secondary px-4 py-2 text-sm font-semibold text-secondary-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
            >
              <Ban className="h-4 w-4" />
              {blocking ? "Dang chan..." : `Chan ngay da chon`}
            </button>
            <button
              type="button"
              onClick={() => doUnblock(selectedDates)}
              disabled={selectedDates.length === 0 || unblocking}
              className="flex items-center gap-2 rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:bg-accent"
            >
              <CheckCircle className="h-4 w-4" />
              {unblocking ? "Dang mo..." : `Mo lai ngay da chon`}
            </button>
            <button
              type="button"
              onClick={() => { setSelectedDates([]); setBanner(""); }}
              disabled={selectedDates.length === 0}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:text-foreground hover:enabled:bg-accent"
            >
              Bo chon
            </button>
          </div>
        </section>
      </div>
    </WorkspaceSidebar>
  );
}