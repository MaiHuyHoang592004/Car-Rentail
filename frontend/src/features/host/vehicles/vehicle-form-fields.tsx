import type { FieldErrors, UseFormRegister } from "react-hook-form";

import type { VehicleFormState } from "@/features/host/forms";

type VehicleFormFieldsProps = {
  register: UseFormRegister<VehicleFormState>;
  errors: FieldErrors<VehicleFormState>;
  disabledStatus?: boolean;
};

export function VehicleFormFields({
  register,
  errors,
  disabledStatus = false,
}: VehicleFormFieldsProps) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Category</label>
        <input
          type="text"
          {...register("category")}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.category ? <p className="mt-1 text-xs text-rose-700">{errors.category.message}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Make</label>
        <input
          type="text"
          {...register("make")}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.make ? <p className="mt-1 text-xs text-rose-700">{errors.make.message}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Model</label>
        <input
          type="text"
          {...register("model")}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.model ? <p className="mt-1 text-xs text-rose-700">{errors.model.message}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Year</label>
        <input
          type="number"
          {...register("year")}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.year ? <p className="mt-1 text-xs text-rose-700">{errors.year.message}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Transmission</label>
        <select
          {...register("transmission")}
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
          {...register("fuelType")}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Seats</label>
        <input
          type="number"
          {...register("seats")}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.seats ? <p className="mt-1 text-xs text-rose-700">{errors.seats.message}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Status</label>
        <select
          {...register("status")}
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
          {...register("city")}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.city ? <p className="mt-1 text-xs text-rose-700">{errors.city.message}</p> : null}
      </div>

      <div>
        <label className="mb-1 block text-sm font-semibold text-foreground">Plate number</label>
        <input
          type="text"
          {...register("plateNumber")}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
        {errors.plateNumber ? (
          <p className="mt-1 text-xs text-rose-700">{errors.plateNumber.message}</p>
        ) : null}
      </div>

      <div className="sm:col-span-2">
        <label className="mb-1 block text-sm font-semibold text-foreground">VIN (optional)</label>
        <input
          type="text"
          {...register("vin")}
          className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
        />
      </div>
    </div>
  );
}
