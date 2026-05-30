"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { AlertCircle, CheckCircle, FileCheck2, ListChecks, Scale, Users } from "lucide-react";

import { AppShell } from "@/components/rentflow/app-shell";
import { adminListListings } from "@/features/admin/listings/api";
import { adminListUsers } from "@/features/admin/users/api";
import { getListingStatusLabel } from "@/lib/display-labels";
import { formatMoney } from "@/lib/formatters";

export function AdminDashboardPageView() {
  const { data: pendingData, isLoading: loadingPending } = useQuery({
    queryKey: ["admin", "dashboard", "listings"],
    queryFn: () => adminListListings({ status: "PENDING_APPROVAL", size: 5 }),
  });

  const { data: usersData, isLoading: loadingUsers } = useQuery({
    queryKey: ["admin", "dashboard", "users"],
    queryFn: () => adminListUsers({ status: "ALL", role: "ALL", page: 0, size: 1 }),
  });

  const pendingCount = pendingData?.totalElements ?? 0;
  const totalUsers = usersData?.totalElements ?? 0;

  return (
    <AppShell activePath="/admin">
      <div className="space-y-6">
        {/* Metric cards */}
        <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <article className="rounded-xl border border-border bg-card px-4 py-3 shadow-sm">
            <div className="flex items-center gap-2">
              <AlertCircle className="h-4 w-4 text-amber-600" />
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                Tin cho duyet
              </p>
            </div>
            <p className="mt-2 text-2xl font-bold text-foreground">
              {loadingPending ? "..." : pendingCount}
            </p>
          </article>

          <article className="rounded-xl border border-border bg-card px-4 py-3 shadow-sm">
            <div className="flex items-center gap-2">
              <Users className="h-4 w-4 text-blue-600" />
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                Nguoi dung
              </p>
            </div>
            <p className="mt-2 text-2xl font-bold text-foreground">
              {loadingUsers ? "..." : totalUsers}
            </p>
          </article>

          <Link
            href="/admin/listings"
            className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 shadow-sm transition-colors hover:border-amber-300"
          >
            <div className="flex items-center gap-2">
              <ListChecks className="h-4 w-4 text-amber-700" />
              <p className="text-xs font-semibold uppercase tracking-wide text-amber-800">
                Duyet tin
              </p>
            </div>
            <p className="mt-1 text-lg font-bold text-amber-900">
              {loadingPending ? "..." : `${pendingCount} tin`}
            </p>
          </Link>

          <Link
            href="/admin/users"
            className="rounded-xl border border-border bg-card px-4 py-3 shadow-sm transition-colors hover:bg-accent"
          >
            <div className="flex items-center gap-2">
              <Users className="h-4 w-4 text-muted-foreground" />
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                Nguoi dung
              </p>
            </div>
            <p className="mt-1 text-lg font-bold text-foreground">
              {loadingUsers ? "..." : `${totalUsers} tai khoan`}
            </p>
          </Link>
        </section>

        {/* Pending listings action required */}
        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertCircle className="h-4 w-4 text-amber-600" />
              <h2 className="text-base font-bold text-foreground">Tin cho duyet</h2>
              {!loadingPending && pendingCount > 0 && (
                <span className="ml-1 flex h-5 w-5 items-center justify-center rounded-full bg-amber-100 text-[10px] font-bold text-amber-800">
                  {pendingCount}
                </span>
              )}
            </div>
            <Link
              href="/admin/listings"
              className="text-xs font-semibold text-primary hover:underline"
            >
              Xem tat ca
            </Link>
          </div>

          {loadingPending ? (
            <p className="mt-3 text-sm text-muted-foreground">Dang tai...</p>
          ) : (pendingData?.listings ?? []).length === 0 ? (
            <div className="mt-3 flex items-center gap-2 text-sm text-emerald-700">
              <CheckCircle className="h-4 w-4 text-emerald-600" />
              Khong co tin nao dang cho duyet.
            </div>
          ) : (
            <div className="mt-3 space-y-2">
              {(pendingData?.listings ?? []).map((listing) => (
                <Link
                  key={listing.id}
                  href={`/admin/listings/${listing.id}`}
                  className="flex items-center justify-between rounded-lg border border-border bg-background px-3 py-2.5 text-sm transition-colors hover:bg-accent"
                >
                  <div className="min-w-0">
                    <p className="font-semibold text-foreground truncate">{listing.title}</p>
                    <p className="text-xs text-muted-foreground">
                      {listing.city} &middot; {formatMoney(listing.basePricePerDay, listing.currency)} / ngay
                    </p>
                  </div>
                  <span className="ml-3 shrink-0 rounded-full bg-amber-100 px-2.5 py-0.5 text-[10px] font-semibold text-amber-800">
                    Cho duyet
                  </span>
                </Link>
              ))}
            </div>
          )}
        </section>

        {/* Quick links */}
        <div className="grid gap-4 sm:grid-cols-2">
          <Link
            href="/admin/listings"
            className="rounded-xl border border-border bg-card p-5 shadow-sm transition-colors hover:bg-accent"
          >
            <div className="flex items-center gap-2">
              <ListChecks className="h-4 w-4 text-muted-foreground" />
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Tin dang</p>
            </div>
            <p className="mt-2 text-xl font-bold text-foreground">Duyet tin</p>
            <p className="mt-2 text-xs text-muted-foreground">
              Duyet, tu choi, tam ngung hoac kich hoat lai tin dang.
            </p>
          </Link>
          <Link
            href="/admin/users"
            className="rounded-xl border border-border bg-card p-5 shadow-sm transition-colors hover:bg-accent"
          >
            <div className="flex items-center gap-2">
              <Users className="h-4 w-4 text-muted-foreground" />
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Nguoi dung</p>
            </div>
            <p className="mt-2 text-xl font-bold text-foreground">Quan ly nguoi dung</p>
            <p className="mt-2 text-xs text-muted-foreground">
              Xem danh sach nguoi dung voi bo loc trang thai va vai tro.
            </p>
          </Link>
          <Link
            href="/admin/driver-verifications"
            className="rounded-xl border border-border bg-card p-5 shadow-sm transition-colors hover:bg-accent"
          >
            <div className="flex items-center gap-2">
              <FileCheck2 className="h-4 w-4 text-muted-foreground" />
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Xac minh GPLX</p>
            </div>
            <p className="mt-2 text-xl font-bold text-foreground">Duyet ho so tai xe</p>
            <p className="mt-2 text-xs text-muted-foreground">
              Xem tai lieu, tuoi pending va trang thai SLA cua ho so cho duyet.
            </p>
          </Link>
          <Link
            href="/admin/disputes"
            className="rounded-xl border border-border bg-card p-5 shadow-sm transition-colors hover:bg-accent"
          >
            <div className="flex items-center gap-2">
              <Scale className="h-4 w-4 text-muted-foreground" />
              <p className="text-xs uppercase tracking-wide text-muted-foreground">Khieu nai</p>
            </div>
            <p className="mt-2 text-xl font-bold text-foreground">Xu ly tranh chap</p>
            <p className="mt-2 text-xs text-muted-foreground">
              Xem booking, payment, timeline va giai quyet dispute tu mot man hinh.
            </p>
          </Link>
        </div>
      </div>
    </AppShell>
  );
}
