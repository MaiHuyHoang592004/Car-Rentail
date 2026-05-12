import type { VehicleFormErrors, VehicleFormState } from "@/features/host/forms";

type VehicleFormFieldsProps = {
  form: VehicleFormState;
  errors: VehicleFormErrors;
  onChange: <K extends keyof VehicleFormState>(field: K, value: VehicleFormState[K]) => void;
  disabledStatus?: boolean;
};

export function VehicleFormFields({
  form,
  errors,
  onChange,
  disabledStatus = false,
}: VehicleFormFieldsProps) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Category</label>
        <input
          type="text"
          value={form.category}
          onChange={(event) => onChange("category", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.category ? <p className="mt-1 text-xs text-rose-700">{errors.category}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Make</label>
        <input
          type="text"
          value={form.make}
          onChange={(event) => onChange("make", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.make ? <p className="mt-1 text-xs text-rose-700">{errors.make}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Model</label>
        <input
          type="text"
          value={form.model}
          onChange={(event) => onChange("model", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.model ? <p className="mt-1 text-xs text-rose-700">{errors.model}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Year</label>
        <input
          type="number"
          value={form.year}
          onChange={(event) => onChange("year", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.year ? <p className="mt-1 text-xs text-rose-700">{errors.year}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Transmission</label>
        <select
          value={form.transmission}
          onChange={(event) => onChange("transmission", event.target.value as "AUTO" | "MANUAL")}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        >
          <option value="AUTO">Auto</option>
          <option value="MANUAL">Manual</option>
        </select>
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Fuel type</label>
        <input
          type="text"
          value={form.fuelType}
          onChange={(event) => onChange("fuelType", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Seats</label>
        <input
          type="number"
          value={form.seats}
          onChange={(event) => onChange("seats", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.seats ? <p className="mt-1 text-xs text-rose-700">{errors.seats}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Status</label>
        <select
          value={form.status}
          onChange={(event) => onChange("status", event.target.value as VehicleFormState["status"])}
          disabled={disabledStatus}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        >
          <option value="DRAFT">DRAFT</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="MAINTENANCE">MAINTENANCE</option>
          <option value="SUSPENDED">SUSPENDED</option>
          <option value="ARCHIVED">ARCHIVED</option>
        </select>
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">City</label>
        <input
          type="text"
          value={form.city}
          onChange={(event) => onChange("city", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.city ? <p className="mt-1 text-xs text-rose-700">{errors.city}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Plate number</label>
        <input
          type="text"
          value={form.plateNumber}
          onChange={(event) => onChange("plateNumber", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.plateNumber ? (
          <p className="mt-1 text-xs text-rose-700">{errors.plateNumber}</p>
        ) : null}
      </div>

      <div className="sm:col-span-2">
        <label className="mb-1 block text-sm font-semibold text-foreground">VIN (optional)</label>
        <input
          type="text"
          value={form.vin}
          onChange={(event) => onChange("vin", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
      </div>
    </div>
  );
}
