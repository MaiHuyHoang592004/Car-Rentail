import type { HostListingFormErrors, HostListingFormState } from "@/features/host/forms";
import type { HostVehicleViewModel, HostListingViewModel } from "@/features/host/types";

type ListingFormFieldsProps = {
  form: HostListingFormState;
  errors: HostListingFormErrors;
  onChange: <K extends keyof HostListingFormState>(field: K, value: HostListingFormState[K]) => void;
  vehicleOptions: HostVehicleViewModel[];
  disableVehicleSelect?: boolean;
  readOnly?: boolean;
  listing?: HostListingViewModel;
};

export function ListingFormFields({
  form,
  errors,
  onChange,
  vehicleOptions,
  disableVehicleSelect = false,
  readOnly = false,
  listing,
}: ListingFormFieldsProps) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <div className="sm:col-span-2">
        <label className="mb-1 block text-sm font-semibold text-foreground">Vehicle</label>
        {disableVehicleSelect ? (
          <div className="flex items-center">
            <span className="inline-flex items-center rounded-lg border border-input bg-muted px-3 py-2 text-sm text-foreground">
              {listing?.vehicleLabel || `Vehicle ID: ${listing?.vehicleId ?? ""}`}
            </span>
            <span className="ml-2 text-xs text-muted-foreground">(cannot be changed after creation)</span>
          </div>
        ) : (
          <select
            value={form.vehicleId}
            onChange={(event) => onChange("vehicleId", event.target.value)}
            disabled={readOnly}
            className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
          >
            <option value="">Select active vehicle</option>
            {vehicleOptions.map((vehicle) => (
              <option key={vehicle.id} value={vehicle.id}>
                {vehicle.make} {vehicle.model} ({vehicle.year})
              </option>
            ))}
          </select>
        )}
        {errors.vehicleId ? <p className="mt-1 text-xs text-rose-700">{errors.vehicleId}</p> : null}
      </div>

      <div className="sm:col-span-2">
        <label className="mb-1 block text-sm font-semibold text-foreground">Title</label>
        <input
          type="text"
          value={form.title}
          disabled={readOnly}
          onChange={(event) => onChange("title", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        />
        {errors.title ? <p className="mt-1 text-xs text-rose-700">{errors.title}</p> : null}
      </div>

      <div className="sm:col-span-2">
        <label className="mb-1 block text-sm font-semibold text-foreground">Description</label>
        <textarea
          value={form.description}
          disabled={readOnly}
          onChange={(event) => onChange("description", event.target.value)}
          rows={4}
          className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        />
        {errors.description ? (
          <p className="mt-1 text-xs text-rose-700">{errors.description}</p>
        ) : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">City</label>
        <input
          type="text"
          value={form.city}
          disabled={readOnly}
          onChange={(event) => onChange("city", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        />
        {errors.city ? <p className="mt-1 text-xs text-rose-700">{errors.city}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Address</label>
        <input
          type="text"
          value={form.address}
          disabled={readOnly}
          onChange={(event) => onChange("address", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        />
        {errors.address ? <p className="mt-1 text-xs text-rose-700">{errors.address}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Base price/day (VND)</label>
        <input
          type="number"
          value={form.basePricePerDay}
          disabled={readOnly}
          onChange={(event) => onChange("basePricePerDay", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        />
        {errors.basePricePerDay ? (
          <p className="mt-1 text-xs text-rose-700">{errors.basePricePerDay}</p>
        ) : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Daily km limit</label>
        <input
          type="number"
          value={form.dailyKmLimit}
          disabled={readOnly}
          onChange={(event) => onChange("dailyKmLimit", event.target.value)}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        />
        {errors.dailyKmLimit ? (
          <p className="mt-1 text-xs text-rose-700">{errors.dailyKmLimit}</p>
        ) : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Cancellation policy</label>
        <select
          value={form.cancellationPolicy}
          disabled={readOnly}
          onChange={(event) =>
            onChange("cancellationPolicy", event.target.value as HostListingFormState["cancellationPolicy"])
          }
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        >
          <option value="FLEXIBLE">FLEXIBLE</option>
          <option value="MODERATE">MODERATE</option>
          <option value="STRICT">STRICT</option>
        </select>
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Instant book</label>
        <select
          value={form.instantBook ? "YES" : "NO"}
          disabled={readOnly}
          onChange={(event) => onChange("instantBook", event.target.value === "YES")}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        >
          <option value="NO">No</option>
          <option value="YES">Yes</option>
        </select>
      </div>
    </div>
  );
}
