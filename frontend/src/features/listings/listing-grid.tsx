import Link from "next/link";
import { SearchX } from "lucide-react";

import { ListingCard } from "@/features/listings/listing-card";
import type { ListingCardViewModel } from "@/features/listings/types";

type ListingGridProps = {
  listings: ListingCardViewModel[];
  emptyTitle?: string;
  emptyDescription?: string;
  onReset?: () => void;
};

export function ListingGrid({
  listings,
  emptyTitle = "Không có xe nào phù hợp",
  emptyDescription = "Thay đổi thành phố, ngày hoặc khoảng giá rồi thử lại.",
  onReset,
}: ListingGridProps) {
  if (listings.length === 0) {
    return (
      <section className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border bg-card p-12 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-muted">
          <SearchX className="h-7 w-7 text-muted-foreground" />
        </div>
        <h3 className="mt-4 text-lg font-bold text-foreground">{emptyTitle}</h3>
        <p className="mt-1 max-w-xs text-sm text-muted-foreground">
          {emptyDescription}
        </p>
        {onReset ? (
          <button
            type="button"
            onClick={onReset}
            className="mt-6 rounded-full border border-primary bg-primary px-6 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Đặt lại bộ lọc
          </button>
        ) : (
          <Link
            href="/listings"
            className="mt-6 rounded-full border border-primary bg-primary px-6 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Đặt lại bộ lọc
          </Link>
        )}
      </section>
    );
  }

  return (
    <section className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
      {listings.map((listing) => (
        <ListingCard key={listing.id} listing={listing} />
      ))}
    </section>
  );
}
