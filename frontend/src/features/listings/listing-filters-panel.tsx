"use client";

import type { UseFormReturn } from "react-hook-form";
import { SlidersHorizontal, MapPin, X } from "lucide-react";

import type { ListingFilterState } from "@/features/listings/types";

type ListingFiltersPanelProps = {
  form: UseFormReturn<ListingFilterState>;
  onReset: () => void;
};

const CATEGORY_OPTIONS = [
  { value: "ALL", label: "Tất cả" },
  { value: "SEDAN", label: "Sedan" },
  { value: "SUV", label: "SUV" },
  { value: "HATCHBACK", label: "Hatchback" },
  { value: "LUXURY", label: "Hạng sang" },
  { value: "SPORTS", label: "Thể thao" },
  { value: "MPV", label: "MPV" },
  { value: "PICKUP", label: "Bán tải" },
];

const TRANSMISSION_OPTIONS = [
  { value: "ALL", label: "Mọi hộp số" },
  { value: "AUTO", label: "Tự động" },
  { value: "MANUAL", label: "Số sàn" },
];

const FUEL_OPTIONS = [
  { value: "ALL", label: "Mọi loại nhiên liệu" },
  { value: "GASOLINE", label: "Xăng" },
  { value: "DIESEL", label: "Dầu" },
  { value: "EV", label: "Điện" },
  { value: "HYBRID", label: "Hybrid" },
];

export function ListingFiltersPanel({ form, onReset }: ListingFiltersPanelProps) {
  const { register, watch, setValue } = form;

  const hasActiveFilters =
    watch("city") ||
    watch("pickupDate") ||
    watch("returnDate") ||
    watch("category") !== "ALL" ||
    watch("transmission") !== "ALL" ||
    watch("fuelType") !== "ALL" ||
    watch("seats") ||
    watch("minPrice") ||
    watch("maxPrice");

  return (
    <section className="rounded-xl border border-border bg-card shadow-sm">
      <div className="sticky top-0 z-10 flex items-center justify-between border-b border-border bg-card px-4 py-3">
        <div className="flex items-center gap-2">
          <SlidersHorizontal className="h-4 w-4 text-muted-foreground" />
          <h2 className="text-sm font-semibold text-foreground">Bộ lọc tìm kiếm</h2>
        </div>
        {hasActiveFilters ? (
          <button
            type="button"
            onClick={onReset}
            className="flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs font-medium text-foreground transition-colors hover:bg-accent"
          >
            <X className="h-3 w-3" />
            Xóa lọc
          </button>
        ) : null}
      </div>

      <div className="p-4 space-y-5">
        <div className="relative">
          <MapPin className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground pointer-events-none" />
          <input
            type="text"
            placeholder="Thành phố hoặc địa điểm"
            {...register("city")}
            className="h-11 w-full rounded-lg border border-input bg-background pl-9 pr-3 text-sm text-foreground placeholder:text-muted-foreground outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-50"
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-medium text-muted-foreground">Ngày nhận xe</label>
            <input
              type="date"
              {...register("pickupDate")}
              className="h-11 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-medium text-muted-foreground">Ngày trả xe</label>
            <input
              type="date"
              {...register("returnDate")}
              className="h-11 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
            />
          </div>
        </div>

        <div className="border-t border-border pt-4">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Loại xe
          </p>
          <div className="flex flex-wrap gap-2">
            {CATEGORY_OPTIONS.map((opt) => (
              <label
                key={opt.value}
                className={`cursor-pointer rounded-full border px-3 py-1.5 text-xs font-medium transition-colors ${
                  watch("category") === opt.value
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-border bg-background text-foreground hover:border-primary/50"
                }`}
              >
                <input
                  type="radio"
                  {...register("category")}
                  value={opt.value}
                  className="sr-only"
                />
                {opt.label}
              </label>
            ))}
          </div>
        </div>

        <div className="border-t border-border pt-4">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Hộp số
          </p>
          <div className="flex flex-wrap gap-2">
            {TRANSMISSION_OPTIONS.map((opt) => (
              <label
                key={opt.value}
                className={`cursor-pointer rounded-full border px-3 py-1.5 text-xs font-medium transition-colors ${
                  watch("transmission") === opt.value
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-border bg-background text-foreground hover:border-primary/50"
                }`}
              >
                <input
                  type="radio"
                  {...register("transmission")}
                  value={opt.value}
                  className="sr-only"
                />
                {opt.label}
              </label>
            ))}
          </div>
        </div>

        <div className="border-t border-border pt-4">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Nhiên liệu
          </p>
          <div className="flex flex-wrap gap-2">
            {FUEL_OPTIONS.map((opt) => (
              <label
                key={opt.value}
                className={`cursor-pointer rounded-full border px-3 py-1.5 text-xs font-medium transition-colors ${
                  watch("fuelType") === opt.value
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-border bg-background text-foreground hover:border-primary/50"
                }`}
              >
                <input
                  type="radio"
                  {...register("fuelType")}
                  value={opt.value}
                  className="sr-only"
                />
                {opt.label}
              </label>
            ))}
          </div>
        </div>

        <div className="border-t border-border pt-4">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Số chỗ
          </p>
          <input
            type="number"
            min={1}
            max={30}
            placeholder="Số chỗ ngồi"
            {...register("seats")}
            className="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground placeholder:text-muted-foreground outline-none ring-primary/30 focus:ring-2"
          />
        </div>

        <div className="border-t border-border pt-4">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Khoảng giá (VND / ngày)
          </p>
          <div className="grid grid-cols-2 gap-3">
            <input
              type="number"
              min={0}
              placeholder="Tối thiểu"
              {...register("minPrice")}
              className="h-11 rounded-lg border border-input bg-background px-3 text-sm text-foreground placeholder:text-muted-foreground outline-none ring-primary/30 focus:ring-2"
            />
            <input
              type="number"
              min={0}
              placeholder="Tối đa"
              {...register("maxPrice")}
              className="h-11 rounded-lg border border-input bg-background px-3 text-sm text-foreground placeholder:text-muted-foreground outline-none ring-primary/30 focus:ring-2"
            />
          </div>
        </div>
      </div>
    </section>
  );
}
