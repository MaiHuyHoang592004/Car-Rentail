import type { ListingFilterState } from "@/features/listings/types";

type ListingFiltersPanelProps = {
  value: ListingFilterState;
  onChange: (next: ListingFilterState) => void;
  onReset: () => void;
};

type InputKey = keyof ListingFilterState;

function updateValue(
  current: ListingFilterState,
  key: InputKey,
  value: string,
  onChange: (next: ListingFilterState) => void,
) {
  onChange({ ...current, [key]: value });
}

export function ListingFiltersPanel({ value, onChange, onReset }: ListingFiltersPanelProps) {
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
          value={value.city}
          onChange={(event) => updateValue(value, "city", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />

        <input
          type="date"
          value={value.pickupDate}
          onChange={(event) => updateValue(value, "pickupDate", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />

        <input
          type="date"
          value={value.returnDate}
          onChange={(event) => updateValue(value, "returnDate", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />

        <select
          value={value.category}
          onChange={(event) => updateValue(value, "category", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        >
          <option value="ALL">Tất cả phân loại</option>
          <option value="SEDAN">Sedan</option>
          <option value="SUV">SUV</option>
          <option value="LUXURY">Hạng sang</option>
        </select>

        <select
          value={value.transmission}
          onChange={(event) => updateValue(value, "transmission", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        >
          <option value="ALL">Mọi hộp số</option>
          <option value="AUTO">Tự động</option>
          <option value="MANUAL">Số sàn</option>
        </select>

        <select
          value={value.fuelType}
          onChange={(event) => updateValue(value, "fuelType", event.target.value, onChange)}
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
          value={value.seats}
          onChange={(event) => updateValue(value, "seats", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />

        <input
          type="number"
          min={0}
          placeholder="Giá tối thiểu (VND/ngày)"
          value={value.minPrice}
          onChange={(event) => updateValue(value, "minPrice", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />

        <input
          type="number"
          min={0}
          placeholder="Giá tối đa (VND/ngày)"
          value={value.maxPrice}
          onChange={(event) => updateValue(value, "maxPrice", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />
      </div>
    </section>
  );
}
