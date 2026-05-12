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
        <h2 className="text-lg font-bold text-foreground">Filter Listings</h2>
        <button
          type="button"
          onClick={onReset}
          className="text-xs font-semibold uppercase tracking-wide text-primary hover:underline"
        >
          Reset
        </button>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        <input
          type="text"
          placeholder="City"
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
          <option value="ALL">All categories</option>
          <option value="SEDAN">Sedan</option>
          <option value="SUV">SUV</option>
          <option value="LUXURY">Luxury</option>
        </select>

        <select
          value={value.transmission}
          onChange={(event) => updateValue(value, "transmission", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        >
          <option value="ALL">Any transmission</option>
          <option value="AUTO">Automatic</option>
          <option value="MANUAL">Manual</option>
        </select>

        <select
          value={value.fuelType}
          onChange={(event) => updateValue(value, "fuelType", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        >
          <option value="ALL">Any fuel type</option>
          <option value="GASOLINE">Gasoline</option>
          <option value="DIESEL">Diesel</option>
          <option value="EV">Electric</option>
        </select>

        <input
          type="number"
          min={1}
          placeholder="Seats"
          value={value.seats}
          onChange={(event) => updateValue(value, "seats", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />

        <input
          type="number"
          min={0}
          placeholder="Min VND/day"
          value={value.minPrice}
          onChange={(event) => updateValue(value, "minPrice", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />

        <input
          type="number"
          min={0}
          placeholder="Max VND/day"
          value={value.maxPrice}
          onChange={(event) => updateValue(value, "maxPrice", event.target.value, onChange)}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
        />
      </div>
    </section>
  );
}
