import { ChevronLeft, ChevronRight } from "lucide-react";

import type { ListingDetailViewModel } from "@/features/listings/types";

type AvailabilityPreviewProps = {
  listing: ListingDetailViewModel;
};

const WEEKDAYS = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];

export function AvailabilityPreview({ listing }: AvailabilityPreviewProps) {
  const now = new Date();
  const monthLabel = now.toLocaleDateString("vi-VN", { month: "long", year: "numeric" });
  const daysByDayOfMonth = new Map(
    listing.availability.days.map((day) => [new Date(day.date).getDate(), day.state]),
  );
  const calendarDays = Array.from({ length: 35 }, (_, index) => {
    const dayNumber = index - 5;
    if (dayNumber < 1) return { label: String(24 + index), disabled: true, state: "UNAVAILABLE" };
    if (dayNumber > 30) return { label: String(dayNumber - 30), disabled: true, state: "UNAVAILABLE" };
    return {
      label: String(dayNumber),
      disabled: daysByDayOfMonth.get(dayNumber) === "BLOCKED",
      state: daysByDayOfMonth.get(dayNumber) ?? "FREE",
    };
  });

  return (
    <section className="space-y-4">
      <h2 className="border-l-4 border-primary pl-4 text-xl font-semibold text-foreground">
        Lịch trống của xe
      </h2>
      <div className="rounded-xl border border-border bg-white p-5">
        <div className="mb-4 flex items-center justify-between">
          <span className="text-sm font-semibold capitalize text-foreground">{monthLabel}</span>
          <div className="flex gap-2">
            <button type="button" className="rounded-full p-1.5 text-muted-foreground hover:bg-muted">
              <ChevronLeft className="h-4 w-4" />
            </button>
            <button type="button" className="rounded-full p-1.5 text-muted-foreground hover:bg-muted">
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>

        <div className="grid grid-cols-7 gap-1 text-center">
          {WEEKDAYS.map((weekday) => (
            <div key={weekday} className="py-1 text-xs font-medium text-muted-foreground">
              {weekday}
            </div>
          ))}
          {calendarDays.map((day, index) => (
            <div
              key={`${day.label}-${index}`}
              className={[
                "rounded-lg p-2 text-sm transition-colors",
                day.disabled
                  ? "bg-muted text-muted-foreground/60 line-through"
                  : day.state === "FREE"
                    ? "cursor-pointer text-foreground hover:bg-[#dbe1ff]"
                    : "bg-[#e0e3e5] text-muted-foreground",
                index === 7 ? "bg-[#dbe1ff] font-bold text-primary" : "",
              ].join(" ")}
            >
              {day.label}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
