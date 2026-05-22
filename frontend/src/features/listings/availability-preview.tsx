import { StatusBadge } from "@/components/rentflow/status-badge";
import type { ListingDetailViewModel } from "@/features/listings/types";

type AvailabilityPreviewProps = {
  listing: ListingDetailViewModel;
};

export function AvailabilityPreview({ listing }: AvailabilityPreviewProps) {
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-foreground">Lịch khả dụng</h2>
        <p className="text-xs uppercase tracking-wide text-muted-foreground">
          {listing.availability.from} - {listing.availability.to}
        </p>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
        {listing.availability.days.map((day) => (
          <div key={day.date} className="rounded-lg border border-border bg-background p-2">
            <p className="text-xs font-semibold text-muted-foreground">{day.date}</p>
            <div className="mt-2">
              <StatusBadge status={day.state} />
            </div>
          </div>
        ))}
      </div>

      <p className="mt-4 text-sm text-muted-foreground">
        Khách chưa đăng nhập sẽ được chuyển sang trang đăng nhập và giữ nguyên điểm đến.
      </p>
    </section>
  );
}
