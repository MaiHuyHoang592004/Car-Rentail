import type {
  FieldErrors,
  UseFormRegister,
  UseFormSetValue,
  UseFormWatch,
} from "react-hook-form";
import type { HostListingFormState } from "@/features/host/forms";
import type { HostListingViewModel } from "@/features/host/types";
import type { HostVehicleSelectOption } from "@/features/host/vehicles/types";

type ListingFormFieldsProps = {
  register: UseFormRegister<HostListingFormState>;
  errors: FieldErrors<HostListingFormState>;
  setValue: UseFormSetValue<HostListingFormState>;
  watch: UseFormWatch<HostListingFormState>;
  vehicleOptions: HostVehicleSelectOption[];
  disableVehicleSelect?: boolean;
  readOnly?: boolean;
  listing?: HostListingViewModel;
};

function SectionLabel({ children }: { children: React.ReactNode }) {
  return <h3 className="text-sm font-bold uppercase tracking-wide text-muted-foreground mb-3">{children}</h3>;
}

function FieldLabel({ children }: { children: React.ReactNode }) {
  return <label className="mb-1 block text-sm font-semibold text-foreground">{children}</label>;
}

function ErrorMsg({ msg }: { msg?: string }) {
  if (!msg) return null;
  return <p className="mt-1 text-xs text-rose-700">{msg}</p>;
}

function FormRow({ children }: { children: React.ReactNode }) {
  return <div className="grid gap-3 sm:grid-cols-2">{children}</div>;
}

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
    <div className="space-y-6">
      {/* Vehicle */}
      {!disableVehicleSelect && (
        <section>
          <SectionLabel>Xe</SectionLabel>
          <div className="sm:col-span-2">
            <FieldLabel>Xe</FieldLabel>
            <select
              {...register("vehicleId")}
              disabled={readOnly}
              className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
            >
              <option value="">Chon xe hoat dong</option>
              {vehicleOptions.map((vehicle) => (
                <option key={vehicle.id} value={vehicle.id}>
                  {vehicle.label}
                </option>
              ))}
            </select>
            <ErrorMsg msg={errors.vehicleId?.message} />
          </div>
        </section>
      )}

      {disableVehicleSelect && listing ? (
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs text-muted-foreground">Xe da chon</p>
          <p className="text-sm font-semibold text-foreground">{listing.vehicleLabel}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">(khong the thay doi sau khi tao)</p>
        </div>
      ) : null}

      {/* Section 1: Thong tin hien thi */}
      <section>
        <SectionLabel>Thong tin hien thi</SectionLabel>
        <div className="space-y-3">
          <div>
            <FieldLabel>Tieu de</FieldLabel>
            <input
              type="text"
              placeholder="VD: Toyota Vios 2022 dien hoa"
              {...register("title")}
              disabled={readOnly}
              className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
            />
            <ErrorMsg msg={errors.title?.message} />
          </div>
          <div>
            <FieldLabel>Mo ta</FieldLabel>
            <textarea
              placeholder="Mo ta xe chi tiet..."
              rows={4}
              {...register("description")}
              disabled={readOnly}
              className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
            />
            <ErrorMsg msg={errors.description?.message} />
          </div>
        </div>
      </section>

      {/* Section 2: Gia & dieu kien */}
      <section>
        <SectionLabel>Gia &amp; dieu kien</SectionLabel>
        <div className="space-y-3">
          <FormRow>
            <div>
              <FieldLabel>Gia / ngay (VND)</FieldLabel>
              <input
                type="number"
                placeholder="VD: 350000"
                {...register("basePricePerDay")}
                disabled={readOnly}
                className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
              />
              <ErrorMsg msg={errors.basePricePerDay?.message} />
            </div>
            <div>
              <FieldLabel>Gioi han km / ngay</FieldLabel>
              <input
                type="number"
                placeholder="VD: 300"
                {...register("dailyKmLimit")}
                disabled={readOnly}
                className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
              />
              <ErrorMsg msg={errors.dailyKmLimit?.message} />
            </div>
          </FormRow>
          <FormRow>
            <div>
              <FieldLabel>Chinh sach huy</FieldLabel>
              <select
                {...register("cancellationPolicy")}
                disabled={readOnly}
                className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
              >
                <option value="FLEXIBLE">Linh hoat (Flexible)</option>
                <option value="MODERATE">Trung binh (Moderate)</option>
                <option value="STRICT">Nghiem ngat (Strict)</option>
              </select>
            </div>
            <div>
              <FieldLabel>Cho phep dat ngay</FieldLabel>
              <select
                value={watch("instantBook") ? "YES" : "NO"}
                disabled={readOnly}
                onChange={(e) => setValue("instantBook", e.target.value === "YES", { shouldDirty: true })}
                className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
              >
                <option value="NO">Khong</option>
                <option value="YES">Co</option>
              </select>
            </div>
          </FormRow>
        </div>
      </section>

      {/* Section 3: Dia diem */}
      <section>
        <SectionLabel>Dia diem</SectionLabel>
        <div className="space-y-3">
          <FormRow>
            <div>
              <FieldLabel>Thanh pho</FieldLabel>
              <input
                type="text"
                placeholder="VD: Ho Chi Minh"
                {...register("city")}
                disabled={readOnly}
                className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
              />
              <ErrorMsg msg={errors.city?.message} />
            </div>
            <div>
              <FieldLabel>Dia chi cu the</FieldLabel>
              <input
                type="text"
                placeholder="VD: 123 Nguyen Hue, Q1"
                {...register("address")}
                disabled={readOnly}
                className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
              />
              <ErrorMsg msg={errors.address?.message} />
            </div>
          </FormRow>
        </div>
      </section>
    </div>
  );
}
