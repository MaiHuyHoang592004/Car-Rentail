"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { ArrowLeft, Ban, CalendarRange, CheckCircle, Clock3, Plus } from "lucide-react";

import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { AvailabilityDateCell } from "@/features/host/components/availability-date-cell";
import {
  blockAvailabilityDates,
  blockAvailabilityRange,
  extendAvailabilityThroughDate,
  getHostAvailabilityByListingId,
  unblockAvailabilityRange,
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
  const [rangeFrom, setRangeFrom] = useState(from);
  const [rangeTo, setRangeTo] = useState(to);
  const [extendThrough, setExtendThrough] = useState(to);

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

  const { mutate: doBlockRange, isPending: blockingRange } = useMutation({
    mutationFn: ({ start, end }: { start: string; end: string }) =>
      blockAvailabilityRange(listingId, start, end),
    onSuccess: (count) => {
      setBanner(`Da chan ${count} ngay trong khoang da chon.`);
      queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId, "availability"] });
    },
    onError: (err: Error) => {
      toast.error(err.message ?? "Loi khi chan theo khoang.");
    },
  });

  const { mutate: doUnblockRange, isPending: unblockingRange } = useMutation({
    mutationFn: ({ start, end }: { start: string; end: string }) =>
      unblockAvailabilityRange(listingId, start, end),
    onSuccess: (count) => {
      setBanner(`Da mo ${count} ngay trong khoang da chon.`);
      queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId, "availability"] });
    },
    onError: (err: Error) => {
      toast.error(err.message ?? "Loi khi mo theo khoang.");
    },
  });

  const { mutate: doExtend, isPending: extending } = useMutation({
    mutationFn: (throughDate: string) => extendAvailabilityThroughDate(listingId, throughDate),
    onSuccess: (count) => {
      setBanner(
        count > 0
          ? `Da them ${count} ngay availability moi.`
          : "Khoang availability hien tai da bao phu den ngay da chon.",
      );
      queryClient.invalidateQueries({ queryKey: ["host", "listings", listingId, "availability"] });
    },
    onError: (err: Error) => {
      toast.error(err.message ?? "Loi khi mo rong lich.");
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
  const holdDays = days.filter((day) => day.status === "HOLD" && day.bookingId);

  function toggleDate(date: string) {
    setSelectedDates((prev) =>
      prev.includes(date) ? prev.filter((v) => v !== date) : [...prev, date],
    );
    setBanner("");
  }

  function handleRangeAction(action: "block" | "unblock") {
    if (!rangeFrom || !rangeTo) {
      toast.error("Can chon day du tu ngay va den ngay.");
      return;
    }
    if (rangeTo < rangeFrom) {
      toast.error("Den ngay phai lon hon hoac bang tu ngay.");
      return;
    }
    setBanner("");
    if (action === "block") {
      doBlockRange({ start: rangeFrom, end: rangeTo });
      return;
    }
    doUnblockRange({ start: rangeFrom, end: rangeTo });
  }

  function buildExtendDate(daysAhead: number): string {
    const start = extendThrough ? new Date(`${extendThrough}T00:00:00`) : new Date();
    start.setDate(start.getDate() + daysAhead);
    return start.toISOString().split("T")[0];
  }

  function formatExpiry(value?: string): string {
    if (!value) {
      return "Khong ro han giu";
    }
    return new Date(value).toLocaleString("vi-VN", {
      hour: "2-digit",
      minute: "2-digit",
      day: "2-digit",
      month: "2-digit",
    });
  }

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

          <div className="mt-4 grid gap-4 xl:grid-cols-[1.2fr_1fr]">
            <section className="rounded-2xl border border-border/70 bg-background/70 p-4">
              <div className="flex items-center gap-2">
                <CalendarRange className="h-4 w-4 text-primary" />
                <h2 className="text-sm font-semibold text-foreground">Chan / mo theo khoang</h2>
              </div>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <label className="space-y-1 text-sm">
                  <span className="text-muted-foreground">Tu ngay</span>
                  <input
                    type="date"
                    value={rangeFrom}
                    onChange={(event) => setRangeFrom(event.target.value)}
                    className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
                  />
                </label>
                <label className="space-y-1 text-sm">
                  <span className="text-muted-foreground">Den ngay</span>
                  <input
                    type="date"
                    value={rangeTo}
                    onChange={(event) => setRangeTo(event.target.value)}
                    className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
                  />
                </label>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => handleRangeAction("block")}
                  disabled={blockingRange}
                  className="inline-flex items-center gap-2 rounded-full bg-secondary px-4 py-2 text-sm font-semibold text-secondary-foreground hover:enabled:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <Ban className="h-4 w-4" />
                  {blockingRange ? "Dang chan..." : "Chan theo khoang"}
                </button>
                <button
                  type="button"
                  onClick={() => handleRangeAction("unblock")}
                  disabled={unblockingRange}
                  className="inline-flex items-center gap-2 rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:enabled:bg-accent disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <CheckCircle className="h-4 w-4" />
                  {unblockingRange ? "Dang mo..." : "Mo theo khoang"}
                </button>
              </div>
            </section>

            <section className="rounded-2xl border border-border/70 bg-background/70 p-4">
              <div className="flex items-center gap-2">
                <Plus className="h-4 w-4 text-primary" />
                <h2 className="text-sm font-semibold text-foreground">Mo rong availability</h2>
              </div>
              <label className="mt-3 block space-y-1 text-sm">
                <span className="text-muted-foreground">Mo den ngay</span>
                <input
                  type="date"
                  value={extendThrough}
                  onChange={(event) => setExtendThrough(event.target.value)}
                  className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
                />
              </label>
              <div className="mt-3 flex flex-wrap gap-2">
                {[90, 180, 365].map((daysAhead) => (
                  <button
                    key={daysAhead}
                    type="button"
                    onClick={() => setExtendThrough(buildExtendDate(daysAhead))}
                    className="rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-muted-foreground hover:text-foreground hover:bg-accent"
                  >
                    +{daysAhead}
                  </button>
                ))}
                <button
                  type="button"
                  onClick={() => doExtend(extendThrough)}
                  disabled={!extendThrough || extending}
                  className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:enabled:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {extending ? "Dang them..." : "Them ngay"}
                </button>
              </div>
            </section>
          </div>

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

          {holdDays.length > 0 ? (
            <section className="mt-5 rounded-2xl border border-indigo-200 bg-indigo-50/70 p-4">
              <div className="flex items-center gap-2">
                <Clock3 className="h-4 w-4 text-indigo-700" />
                <h2 className="text-sm font-semibold text-indigo-950">Ngay dang HOLD</h2>
              </div>
              <div className="mt-3 space-y-2">
                {holdDays.map((day) => (
                  <div
                    key={day.date}
                    className="flex flex-col gap-2 rounded-xl border border-indigo-200 bg-white/80 px-3 py-3 text-sm text-indigo-950 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div>
                      <p className="font-semibold">
                        {new Date(`${day.date}T00:00:00`).toLocaleDateString("vi-VN")}
                      </p>
                      <p className="text-xs text-indigo-800/80">
                        Het han giu: {formatExpiry(day.expiresAt)}
                      </p>
                    </div>
                    <Link
                      href={`/host/bookings/${day.bookingId}`}
                      className="inline-flex items-center justify-center rounded-full border border-indigo-300 px-3 py-1.5 text-xs font-semibold text-indigo-900 hover:bg-indigo-100"
                    >
                      Xem booking
                    </Link>
                  </div>
                ))}
              </div>
            </section>
          ) : null}

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
