import type { ReactNode } from "react";
import type {
  FieldErrors,
  UseFormRegister,
  UseFormSetValue,
  UseFormWatch,
} from "react-hook-form";

import type { VehicleFormState } from "@/features/host/forms";
import {
  getVehicleModelOptions,
  getVehicleYearOptions,
  VEHICLE_CATEGORY_OPTIONS,
  VEHICLE_CITY_OPTIONS,
  VEHICLE_FUEL_OPTIONS,
  VEHICLE_MAKE_OPTIONS,
  VEHICLE_SEAT_OPTIONS,
  VEHICLE_STATUS_CREATE_OPTIONS,
  VEHICLE_STATUS_OPTIONS,
  VEHICLE_TRANSMISSION_OPTIONS,
} from "@/features/host/vehicles/vehicle-form-options";

type VehicleFormFieldsProps = {
  register: UseFormRegister<VehicleFormState>;
  setValue: UseFormSetValue<VehicleFormState>;
  watch: UseFormWatch<VehicleFormState>;
  errors: FieldErrors<VehicleFormState>;
  hideStatus?: boolean;
  createMode?: boolean;
};

type ChoiceOption = {
  value: string;
  label: string;
  hint?: string;
};

function FieldLabel({ children }: { children: ReactNode }) {
  return <label className="mb-2 block text-sm font-semibold text-foreground">{children}</label>;
}

function TextInput({
  type,
  placeholder,
  ...rest
}: React.InputHTMLAttributes<HTMLInputElement> & {
  placeholder?: string;
}) {
  return (
    <input
      type={type}
      placeholder={placeholder}
      className="h-12 w-full rounded-2xl border border-input bg-background px-4 text-sm outline-none ring-primary/30 focus:ring-2"
      {...rest}
    />
  );
}

function SelectField({
  label,
  placeholder,
  options,
  ...rest
}: {
  label: string;
  placeholder: string;
  options: ChoiceOption[];
} & React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <div>
      <FieldLabel>{label}</FieldLabel>
      <select
        className="h-12 w-full rounded-2xl border border-input bg-background px-4 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        {...rest}
      >
        <option value="">{placeholder}</option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}

function ChoiceGroup({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: ChoiceOption[];
}) {
  return (
    <div>
      <FieldLabel>{label}</FieldLabel>
      <div className="grid gap-2 sm:grid-cols-2">
        {options.map((option) => {
          const active = option.value === value;
          return (
            <button
              key={option.value}
              type="button"
              onClick={() => onChange(option.value)}
              className={[
                "rounded-2xl border px-4 py-3 text-left transition-colors",
                active
                  ? "border-primary bg-primary/8 text-foreground"
                  : "border-border bg-background text-foreground hover:bg-accent",
              ].join(" ")}
            >
              <span className="block text-sm font-semibold">{option.label}</span>
              {option.hint ? (
                <span className="mt-1 block text-xs text-muted-foreground">{option.hint}</span>
              ) : null}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function FormRow({ children }: { children: ReactNode }) {
  return <div className="grid gap-4 sm:grid-cols-2">{children}</div>;
}

function ErrorMsg({ msg }: { msg?: string }) {
  if (!msg) return null;
  return <p className="mt-1 text-xs text-rose-700">{msg}</p>;
}

export function VehicleFormFields({
  register,
  setValue,
  watch,
  errors,
  hideStatus = false,
  createMode = false,
}: VehicleFormFieldsProps) {
  const selectedMake = watch("make");
  const selectedCategory = watch("category");
  const selectedTransmission = watch("transmission");
  const selectedFuelType = watch("fuelType");
  const selectedSeats = watch("seats");
  const selectedStatus = watch("status");
  const modelOptions = getVehicleModelOptions(selectedMake);

  return (
    <div className="space-y-6">
      <section>
        <h3 className="mb-4 text-sm font-bold uppercase tracking-[0.18em] text-muted-foreground">
          Thong tin xe
        </h3>
        <div className="space-y-4">
          <ChoiceGroup
            label="Loai xe"
            value={selectedCategory}
            onChange={(value) => setValue("category", value, { shouldValidate: true })}
            options={VEHICLE_CATEGORY_OPTIONS}
          />
          <ErrorMsg msg={errors.category?.message} />

          <FormRow>
            <div>
              <SelectField
                label="Hang xe"
                placeholder="Chon hang xe"
                options={VEHICLE_MAKE_OPTIONS}
                {...register("make", {
                  onChange: (event) => {
                    const nextMake = event.target.value;
                    const nextModels = getVehicleModelOptions(nextMake);
                    const currentModel = watch("model");
                    if (!nextModels.some((option) => option.value === currentModel)) {
                      setValue("model", "", { shouldValidate: true });
                    }
                  },
                })}
              />
              <ErrorMsg msg={errors.make?.message} />
            </div>
            <div>
              <SelectField
                label="Dong xe"
                placeholder={selectedMake ? "Chon dong xe" : "Chon hang xe truoc"}
                options={modelOptions}
                disabled={!selectedMake}
                {...register("model")}
              />
              <ErrorMsg msg={errors.model?.message} />
            </div>
          </FormRow>

          <FormRow>
            <div>
              <SelectField
                label="Nam san xuat"
                placeholder="Chon nam"
                options={getVehicleYearOptions()}
                {...register("year")}
              />
              <ErrorMsg msg={errors.year?.message} />
            </div>
            <div>
              <SelectField
                label="Thanh pho"
                placeholder="Chon thanh pho"
                options={VEHICLE_CITY_OPTIONS}
                {...register("city")}
              />
              <ErrorMsg msg={errors.city?.message} />
            </div>
          </FormRow>
        </div>
      </section>

      <section>
        <h3 className="mb-4 text-sm font-bold uppercase tracking-[0.18em] text-muted-foreground">
          Dang ky xe
        </h3>
        <div className="space-y-3">
          <FormRow>
            <div>
              <FieldLabel>Bien so</FieldLabel>
              <TextInput type="text" placeholder="VD: 30A-123.45" {...register("plateNumber")} />
              <ErrorMsg msg={errors.plateNumber?.message} />
            </div>
            <div>
              <FieldLabel>VIN (neu co)</FieldLabel>
              <TextInput type="text" placeholder="VD: 1HGBH41JXMN109186" {...register("vin")} />
            </div>
          </FormRow>
        </div>
      </section>

      <section>
        <h3 className="mb-4 text-sm font-bold uppercase tracking-[0.18em] text-muted-foreground">
          Thong so
        </h3>
        <div className="space-y-4">
          <FormRow>
            <ChoiceGroup
              label="Hop so"
              value={selectedTransmission}
              onChange={(value) =>
                setValue("transmission", value as VehicleFormState["transmission"], {
                  shouldValidate: true,
                })
              }
              options={VEHICLE_TRANSMISSION_OPTIONS}
            />
            <ChoiceGroup
              label="Nhien lieu"
              value={selectedFuelType}
              onChange={(value) => setValue("fuelType", value, { shouldValidate: true })}
              options={VEHICLE_FUEL_OPTIONS}
            />
          </FormRow>
          <ErrorMsg msg={errors.fuelType?.message} />

          <ChoiceGroup
            label="So ghe"
            value={selectedSeats}
            onChange={(value) => setValue("seats", value, { shouldValidate: true })}
            options={VEHICLE_SEAT_OPTIONS}
          />
          <ErrorMsg msg={errors.seats?.message} />
        </div>
      </section>

      {!hideStatus ? (
        <section>
          <h3 className="mb-4 text-sm font-bold uppercase tracking-[0.18em] text-muted-foreground">
            Trang thai
          </h3>
          <ChoiceGroup
            label={createMode ? "Cach luu xe" : "Trang thai xe"}
            value={selectedStatus}
            onChange={(value) =>
              setValue("status", value as VehicleFormState["status"], { shouldValidate: true })
            }
            options={createMode ? VEHICLE_STATUS_CREATE_OPTIONS : VEHICLE_STATUS_OPTIONS}
          />
          <ErrorMsg msg={errors.status?.message} />
        </section>
      ) : null}
    </div>
  );
}
