"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { adminListListings } from "@/features/admin/listings/api";

export function AdminDashboardPageView() {
  const { data: listingsData, isLoading: loadingListings } = useQuery({
    queryKey: ["admin", "dashboard", "listings"],
    queryFn: () => adminListListings({ status: "PENDING_APPROVAL", size: 5 }),
  });

  const pendingListings = listingsData?.listings ?? [];

  return (
    <AppShell activePath="/admin">
      <div className="space-y-6">
        <PageHeader
          title="Tổng quan Admin"
          description="Bảng điều khiển quản trị với các truy nhanh."
        />

        {/* Quick links */}
        <div className="grid gap-4 sm:grid-cols-2">
          <Link
            href="/admin/listings"
            className="rounded-xl border border-border bg-card p-5 shadow-sm transition-colors hover:bg-accent"
          >
            <p className="text-xs uppercase tracking-wide text-muted-foreground">Tin đăng</p>
            <p className="mt-1 text-xl font-bold text-foreground">
              {loadingListings ? "…" : listingsData?.totalElements ?? 0} cần xử lý
            </p>
            <p className="mt-2 text-xs text-muted-foreground">
              Duyệt, từ chối, tạm ngưng hoặc kích hoạt lại tin đăng.
            </p>
          </Link>
          <Link
            href="/admin/users"
            className="rounded-xl border border-border bg-card p-5 shadow-sm transition-colors hover:bg-accent"
          >
            <p className="text-xs uppercase tracking-wide text-muted-foreground">Người dùng</p>
            <p className="mt-1 text-xl font-bold text-foreground">Quản lý</p>
            <p className="mt-2 text-xs text-muted-foreground">
              Xem danh sách người dùng với bộ lọc trạng thái và vai trò.
            </p>
          </Link>
        </div>

        {/* Pending listings preview */}
        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold text-foreground">Tin chờ duyệt</h2>
            <Link
              href="/admin/listings"
              className="text-xs font-semibold text-primary hover:underline"
            >
              Xem tất cả
            </Link>
          </div>

          {loadingListings ? (
            <p className="mt-3 text-sm text-muted-foreground">Đang tải...</p>
          ) : pendingListings.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground">
              Không có tin đăng nào đang chờ duyệt.
            </p>
          ) : (
            <div className="mt-3 space-y-2">
              {pendingListings.map((listing) => (
                <Link
                  key={listing.id}
                  href={`/admin/listings/${listing.id}`}
                  className="flex items-center justify-between rounded-lg border border-border bg-background px-3 py-2 text-sm transition-colors hover:bg-accent"
                >
                  <div className="min-w-0">
                    <p className="font-semibold text-foreground truncate">{listing.title}</p>
                    <p className="text-xs text-muted-foreground">
                      {listing.city} · {listing.id.slice(0, 8)}…
                    </p>
                  </div>
                  <span className="ml-3 shrink-0 rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold text-amber-800">
                    {listing.status}
                  </span>
                </Link>
              ))}
            </div>
          )}
        </section>
      </div>
    </AppShell>
  );
}