import { CalendarRange } from "lucide-react";

import { getAvailabilityStatusLabel } from "@/lib/display-labels";
import type { ListingDetailViewModel } from "@/features/listings/types";

type AvailabilityPreviewProps = {
  listing: ListingDetailViewModel;
};

const AVAILABILITY_COLORS: Record<string, string> = {
  FREE: "bg-green-100 text-green-800 border-green-200",
  HOLD: "bg-amber-100 text-amber-800 border-amber-200",
  BOOKED: "bg-red-100 text-red-800 border-red-200",
  BLOCKED: "bg-gray-100 text-gray-500 border-gray-200",
};

export function AvailabilityPreview({ listing }: AvailabilityPreviewProps) {
  const { from, to, days } = listing.availability;

  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <CalendarRange className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-lg font-bold text-foreground">Lịch khả dụng</h2>
        </div>
        <p className="text-xs text-muted-foreground">
          {from} — {to}
        </p>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
        {days.map((day) => {
          const colorClass = AVAILABILITY_COLORS[day.state] ?? "bg-gray-100 text-gray-600 border-gray-200";
          return (
            <div key={day.date} className="rounded-lg border border-border bg-background p-2.5 text-center">
              <p className="text-xs font-semibold text-muted-foreground">{day.date}</p>
              <span
                className={`mt-1.5 inline-block rounded-full border px-2 py-0.5 text-xs font-semibold ${colorClass}`}
              >
                {getAvailabilityStatusLabel(day.state)}
              </span>
            </div>
          );
        })}
      </div>

      <div className="mt-4 flex flex-wrap gap-4 text-xs text-muted-foreground">
        <div className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-full border border-green-300 bg-green-100" />
          Trống
        </div>
        <div className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-full bg-amber-200" />
          Đang giữ
        </div>
        <div className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-full bg-red-200" />
          Đã đặt
        </div>
        <div className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-full bg-gray-200" />
          Không khả dụng
        </div>
      </div>
    </section>
  );
}
