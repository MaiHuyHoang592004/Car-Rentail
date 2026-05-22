import type {
  FieldErrors,
  UseFormRegister,
  UseFormSetValue,
  UseFormWatch,
} from "react-hook-form";

import type { HostListingFormState } from "@/features/host/forms";
import type { HostVehicleViewModel, HostListingViewModel } from "@/features/host/types";

type ListingFormFieldsProps = {
  register: UseFormRegister<HostListingFormState>;
  errors: FieldErrors<HostListingFormState>;
  setValue: UseFormSetValue<HostListingFormState>;
  watch: UseFormWatch<HostListingFormState>;
  vehicleOptions: HostVehicleViewModel[];
  disableVehicleSelect?: boolean;
  readOnly?: boolean;
  listing?: HostListingViewModel;
};

export function ListingFormFields({
  register,
  errors,
  setValue,
  watch,
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
            {...register("vehicleId")}
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
        {errors.vehicleId ? <p className="mt-1 text-xs text-rose-700">{errors.vehicleId.message}</p> : null}
      </div>

      <div className="sm:col-span-2">
        <label className="mb-1 block text-sm font-semibold text-foreground">Title</label>
        <input
          type="text"
          {...register("title")}
          disabled={readOnly}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        />
        {errors.title ? <p className="mt-1 text-xs text-rose-700">{errors.title.message}</p> : null}
      </div>

      <div className="sm:col-span-2">
        <label className="mb-1 block text-sm font-semibold text-foreground">Description</label>
        <textarea
          {...register("description")}
          disabled={readOnly}
          rows={4}
          className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        />
        {errors.description ? (
          <p className="mt-1 text-xs text-rose-700">{errors.description.message}</p>
        ) : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">City</label>
        <input
          type="text"
          {...register("city")}
          disabled={readOnly}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        />
        {errors.city ? <p className="mt-1 text-xs text-rose-700">{errors.city.message}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Address</label>
        <input
          type="text"
          {...register("address")}
          disabled={readOnly}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        />
        {errors.address ? <p className="mt-1 text-xs text-rose-700">{errors.address.message}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Base price/day (VND)</label>
        <input
          type="number"
          {...register("basePricePerDay")}
          disabled={readOnly}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        />
        {errors.basePricePerDay ? (
          <p className="mt-1 text-xs text-rose-700">{errors.basePricePerDay.message}</p>
        ) : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Daily km limit</label>
        <input
          type="number"
          {...register("dailyKmLimit")}
          disabled={readOnly}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        />
        {errors.dailyKmLimit ? (
          <p className="mt-1 text-xs text-rose-700">{errors.dailyKmLimit.message}</p>
        ) : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Cancellation policy</label>
        <select
          {...register("cancellationPolicy")}
          disabled={readOnly}
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
          value={watch("instantBook") ? "YES" : "NO"}
          disabled={readOnly}
          onChange={(event) => setValue("instantBook", event.target.value === "YES", { shouldDirty: true })}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        >
          <option value="NO">No</option>
          <option value="YES">Yes</option>
        </select>
      </div>
    </div>
  );
}
