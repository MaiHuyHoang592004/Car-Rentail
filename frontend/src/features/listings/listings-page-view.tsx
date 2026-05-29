"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { SearchX } from "lucide-react";

import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { AppShell } from "@/components/rentflow/app-shell";
import { DEFAULT_LISTING_FILTERS, searchListings } from "@/features/listings/api";
import { ListingFiltersPanel } from "@/features/listings/listing-filters-panel";
import { ListingGrid } from "@/features/listings/listing-grid";
import { listingFilterSchema } from "@/features/listings/forms";
import type { ListingFilterState } from "@/features/listings/types";
import { ApiError } from "@/lib/api-error";
import { formatMoney } from "@/lib/formatters";

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
  const totalElements = query.data?.totalElements ?? 0;
  const totalPages = query.data?.totalPages ?? 0;

  const hasActiveCity = Boolean(filters.city);
  const hasActivePrice = Boolean(filters.minPrice || filters.maxPrice);

  const summaryParts: string[] = [];
  if (hasActiveCity) summaryParts.push(`tại ${filters.city}`);
  if (filters.minPrice) summaryParts.push(`từ ${formatMoney(Number(filters.minPrice))}`);
  if (filters.maxPrice) summaryParts.push(`đến ${formatMoney(Number(filters.maxPrice))}`);

  return (
    <AppShell activePath="/listings">
      <div className="space-y-5">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold text-foreground">Tìm xe cho thuê</h1>
          <p className="text-sm text-muted-foreground">
            Khám phá xe từ các Host đã được xác minh trên toàn quốc.
          </p>
        </div>

        <div className="flex flex-col gap-5 lg:flex-row lg:items-start">
          <div className="w-full lg:sticky lg:top-4 lg:w-72 lg:shrink-0">
            <ListingFiltersPanel form={filterForm} onReset={handleReset} />
          </div>

          <div className="min-w-0 flex-1 space-y-4">
            {dateRangeInvalid ? (
              <div className="flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                <SearchX className="h-4 w-4 shrink-0" />
                Ngày trả xe phải sau ngày nhận xe. Vui lòng chọn lại ngày.
              </div>
            ) : null}

            {!query.isLoading && !query.isError && !dateRangeInvalid ? (
              <div className="flex items-center justify-between">
                <p className="text-sm text-muted-foreground">
                  {totalElements > 0 ? (
                    <>
                      <span className="font-semibold text-foreground">
                        {totalElements.toLocaleString("vi-VN")}
                      </span>{" "}
                      xe{summaryParts.length > 0 ? ` ${summaryParts.join(", ")}` : ""}
                    </>
                  ) : (
                    "Không tìm thấy xe nào"
                  )}
                </p>
              </div>
            ) : null}

            {query.isLoading ? (
              <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div
                    key={i}
                    className="animate-pulse overflow-hidden rounded-xl border border-border bg-card"
                  >
                    <div className="aspect-[16/10] bg-muted" />
                    <div className="space-y-3 p-4">
                      <div className="h-4 w-3/4 rounded bg-muted" />
                      <div className="h-3 w-1/2 rounded bg-muted" />
                      <div className="h-3 w-2/3 rounded bg-muted" />
                    </div>
                  </div>
                ))}
              </div>
            ) : null}

            {query.isError ? (
              <ApiErrorPanel
                error={query.error instanceof ApiError ? query.error : undefined}
              />
            ) : null}

            {!query.isLoading && !query.isError ? (
              <ListingGrid
                listings={dateRangeInvalid ? [] : listings}
                onReset={handleReset}
              />
            ) : null}

            {!dateRangeInvalid && totalPages > 1 ? (
              <div className="flex items-center justify-between border-t border-border pt-4 text-sm">
                <button
                  type="button"
                  disabled={page === 0 || query.isFetching}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="rounded-full border border-border bg-background px-4 py-2 font-semibold text-foreground disabled:opacity-50 hover:enabled:bg-accent"
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
                  className="rounded-full border border-border bg-background px-4 py-2 font-semibold text-foreground disabled:opacity-50 hover:enabled:bg-accent"
                >
                  Trang sau
                </button>
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </AppShell>
  );
}
