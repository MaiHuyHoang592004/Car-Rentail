"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { HostListingRow } from "@/features/host/components/host-listing-row";
import {
  getHostListings,
  HOST_LISTING_STATUS_FILTERS,
  type HostListingFilterValue,
} from "@/features/host/listings/api";

export function HostListingsPageView() {
  const [statusFilter, setStatusFilter] = useState<HostListingFilterValue>("ALL");

  const { data, isLoading, isError } = useQuery({
    queryKey: ["host", "listings", statusFilter],
    queryFn: () => getHostListings(statusFilter),
  });

  const listings = data?.listings ?? [];

  return (
    <AppShell activePath="/host/listings">
      <div className="space-y-6">
        <PageHeader
          title="Tin đăng của tôi"
          description="Quản lý tin đăng xe theo trạng thái vòng đời."
          actions={
            <Link
              href="/host/listings/new"
              className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
            >
              Tạo tin đăng
            </Link>
          }
        />

        <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <div className="flex flex-wrap gap-2">
            {HOST_LISTING_STATUS_FILTERS.map((status) => {
              const active = statusFilter === status;
              return (
                <button
                  key={status}
                  type="button"
                  onClick={() => setStatusFilter(status)}
                  className={[
                    "rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors",
                    active
                      ? "border-primary bg-primary text-primary-foreground"
                      : "border-border bg-background text-foreground hover:bg-accent",
                  ].join(" ")}
                >
                  {status}
                </button>
              );
            })}
          </div>
        </section>

        {isLoading ? (
          <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
            <p className="text-sm text-muted-foreground">Đang tải tin đăng...</p>
          </section>
        ) : isError ? (
          <section className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
            Không tải được danh sách tin đăng. Vui lòng thử lại.
          </section>
        ) : listings.length === 0 ? (
          <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
            <h2 className="text-xl font-bold text-foreground">Chưa có tin đăng nào</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Đổi bộ lọc hoặc tạo tin đăng mới để tiếp tục.
            </p>
          </section>
        ) : (
          <div className="space-y-3">
            {listings.map((listing) => (
              <HostListingRow key={listing.id} listing={listing} />
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}
