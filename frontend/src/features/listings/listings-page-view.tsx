"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";

import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { DEFAULT_LISTING_FILTERS, searchListings } from "@/features/listings/api";
import { ListingFiltersPanel } from "@/features/listings/listing-filters-panel";
import { ListingGrid } from "@/features/listings/listing-grid";
import { listingFilterSchema } from "@/features/listings/forms";
import type { ListingFilterState } from "@/features/listings/types";
import { ApiError } from "@/lib/api-error";

const PAGE_SIZE = 20;

export function ListingsPageView() {
  const filterForm = useForm<ListingFilterState>({
    resolver: zodResolver(listingFilterSchema),
    defaultValues: DEFAULT_LISTING_FILTERS,
    mode: "onChange",
  });
  const filters = useWatch({ control: filterForm.control }) as ListingFilterState;
  const [page, setPage] = useState(0);
  const dateRangeInvalid =
    Boolean(filters.pickupDate) &&
    Boolean(filters.returnDate) &&
    filters.returnDate <= filters.pickupDate;

  const query = useQuery({
    queryKey: ["listings", "search", filters, page],
    queryFn: ({ signal }) => searchListings(filters, page, PAGE_SIZE, signal),
    enabled: !dateRangeInvalid,
    placeholderData: keepPreviousData,
  });

  useEffect(() => {
    const subscription = filterForm.watch(() => setPage(0));
    return () => subscription.unsubscribe();
  }, [filterForm]);

  function handleReset() {
    filterForm.reset(DEFAULT_LISTING_FILTERS);
    setPage(0);
  }

  const listings = query.data?.content ?? [];
  const totalPages = query.data?.totalPages ?? 0;

  return (
    <AppShell activePath="/listings">
      <div className="space-y-6">
        <PageHeader
          title="Tìm xe"
          description="Duyệt xe đang hoạt động theo thành phố, khoảng giá và thông số."
        />

        <ListingFiltersPanel
          form={filterForm}
          onReset={handleReset}
        />

        {dateRangeInvalid ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
            Ngày trả xe phải sau ngày nhận xe.
          </div>
        ) : null}

        {query.isLoading ? <PageSkeleton message="Đang tải danh sách xe..." /> : null}

        {query.isError ? (
          <ApiErrorPanel error={query.error instanceof ApiError ? query.error : undefined} />
        ) : null}

        {!query.isLoading && !query.isError ? (
          <ListingGrid
            listings={dateRangeInvalid ? [] : listings}
            emptyTitle="Không có xe nào phù hợp"
            emptyDescription="Thay đổi thành phố, ngày hoặc khoảng giá rồi thử lại."
          />
        ) : null}

        {!dateRangeInvalid && totalPages > 1 ? (
          <div className="flex items-center justify-between text-sm">
            <button
              type="button"
              disabled={page === 0 || query.isFetching}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-full border border-border bg-background px-4 py-1.5 font-semibold disabled:opacity-50 hover:enabled:bg-accent"
            >
              Trang trước
            </button>
            <span className="text-muted-foreground">
              Trang {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              disabled={page >= totalPages - 1 || query.isFetching}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-full border border-border bg-background px-4 py-1.5 font-semibold disabled:opacity-50 hover:enabled:bg-accent"
            >
              Trang sau
            </button>
          </div>
        ) : null}
      </div>
    </AppShell>
  );
}
