import type { UseFormReturn } from "react-hook-form";

import type { ListingFilterState } from "@/features/listings/types";

type ListingFiltersPanelProps = {
  form: UseFormReturn<ListingFilterState>;
  onReset: () => void;
};

export function ListingFiltersPanel({ form, onReset }: ListingFiltersPanelProps) {
  return (
    <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold text-foreground">Bộ lọc</h2>
        <button
          type="button"
          onClick={onReset}
          className="text-xs font-semibold uppercase tracking-wide text-primary hover:underline"
        >
          Đặt lại
        </button>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        <input
          type="text"
          placeholder="Thành phố"
          {...form.register("city")}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />

        <input
          type="date"
          {...form.register("pickupDate")}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />

        <input
          type="date"
          {...form.register("returnDate")}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />

        <select
          {...form.register("category")}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        >
          <option value="ALL">Tất cả phân loại</option>
          <option value="SEDAN">Sedan</option>
          <option value="SUV">SUV</option>
          <option value="LUXURY">Hạng sang</option>
        </select>

        <select
          {...form.register("transmission")}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        >
          <option value="ALL">Mọi hộp số</option>
          <option value="AUTO">Tự động</option>
          <option value="MANUAL">Số sàn</option>
        </select>

        <select
          {...form.register("fuelType")}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        >
          <option value="ALL">Mọi loại nhiên liệu</option>
          <option value="GASOLINE">Xăng</option>
          <option value="DIESEL">Dầu</option>
          <option value="EV">Điện</option>
        </select>

        <input
          type="number"
          min={1}
          placeholder="Số chỗ"
          {...form.register("seats")}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />

        <input
          type="number"
          min={0}
          placeholder="Giá tối thiểu (VND/ngày)"
          {...form.register("minPrice")}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />

        <input
          type="number"
          min={0}
          placeholder="Giá tối đa (VND/ngày)"
          {...form.register("maxPrice")}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />
      </div>
    </section>
  );
}
