import type { FieldErrors, UseFormRegister } from "react-hook-form"
import type { VehicleFormState } from "@/features/host/forms"
import { getFuelTypeLabel } from "@/lib/display-labels"
""
type VehicleFormFieldsProps = {
  register: UseFormRegister<VehicleFormState>;
  errors: FieldErrors<VehicleFormState>;
  hideStatus?: boolean;
};
""
function FieldLabel({ children }: { children: React.ReactNode }) {
  return <label className="mb-1 block text-sm font-semibold text-foreground">{children}</label>
}
""
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
      className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2"
      {...rest}
    />
  )
}
""
function SelectField({
  label,
  options,
  ...rest
}: {
  label: string;
  options: { value: string; label: string }[];
} & React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <div>
      <FieldLabel>{label}</FieldLabel>
      <select
        className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none ring-primary/30 focus:ring-2 disabled:cursor-not-allowed disabled:opacity-70"
        {...rest}
      >
        {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </div>
  )
}
""
function FormRow({ children }: { children: React.ReactNode }) {
  return <div className="grid gap-3 sm:grid-cols-2">{children}</div>
}
""
function ErrorMsg({ msg }: { msg?: string }) {
  if (!msg) return null
  return <p className="mt-1 text-xs text-rose-700">{msg}</p>
}
""
export function VehicleFormFields({
  register,
  errors,
  hideStatus = false,
}: VehicleFormFieldsProps) {
  return (
    <div className="space-y-6">
      {/* Section 1: Thong tin xe */}
      <section>
        <h3 className="text-sm font-bold uppercase tracking-wide text-muted-foreground mb-3">Thong tin xe</h3>
        <div className="space-y-3">
          <FormRow>
            <div>
              <FieldLabel>Hang xe</FieldLabel>
              <TextInput type="text" placeholder="VD: Toyota" {...register("make")} />
              <ErrorMsg msg={errors.make?.message} />
            </div>
            <div>
              <FieldLabel>Dong xe</FieldLabel>
              <TextInput type="text" placeholder="VD: Vios" {...register("model")} />
              <ErrorMsg msg={errors.model?.message} />
            </div>
          </FormRow>
          <FormRow>
            <div>
              <FieldLabel>Nam san xuat</FieldLabel>
              <TextInput type="number" placeholder="VD: 2022" {...register("year")} />
              <ErrorMsg msg={errors.year?.message} />
            </div>
            <div>
              <FieldLabel>Loai xe</FieldLabel>
              <TextInput type="text" placeholder="VD: Sedan, SUV" {...register("category")} />
              <ErrorMsg msg={errors.category?.message} />
            </div>
          </FormRow>
        </div>
      </section>
""
      {/* Section 2: Dang ky xe */}
      <section>
        <h3 className="text-sm font-bold uppercase tracking-wide text-muted-foreground mb-3">Dang ky xe</h3>
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
""
      {/* Section 3: Thong so */}
      <section>
        <h3 className="text-sm font-bold uppercase tracking-wide text-muted-foreground mb-3">Thong so</h3>
        <div className="space-y-3">
          <FormRow>
            <SelectField
              label="Hop so"
              options={[
                { value: "AUTO", label: "Tu dong" },
                { value: "MANUAL", label: "So san" },
              ]}
              {...register("transmission")}
            />
            <div>
              <FieldLabel>Nhien lieu</FieldLabel>
              <TextInput type="text" placeholder="VD: Xang, Diesel" {...register("fuelType")} />
            </div>
          </FormRow>
          <FormRow>
            <div>
              <FieldLabel>So ghe</FieldLabel>
              <TextInput type="number" placeholder="VD: 5" {...register("seats")} />
              <ErrorMsg msg={errors.seats?.message} />
            </div>
            <div>
              <FieldLabel>Thanh pho</FieldLabel>
              <TextInput type="text" placeholder="VD: Ho Chi Minh" {...register("city")} />
              <ErrorMsg msg={errors.city?.message} />
            </div>
          </FormRow>
        </div>
      </section>
""
      {!hideStatus ? (
        <section>
          <h3 className="text-sm font-bold uppercase tracking-wide text-muted-foreground mb-3">Trang thai</h3>
          <SelectField
            label="Trang thai"
            options={[
              { value: "DRAFT", label: "Nhap (Draft)" },
              { value: "ACTIVE", label: "Hoat dong (Active)" },
              { value: "MAINTENANCE", label: "Bao tri (Maintenance)" },
              { value: "SUSPENDED", label: "Tam ngung (Suspended)" },
              { value: "ARCHIVED", label: "Luu kho (Archived)" },
            ]}
            {...register("status")}
          />
        </section>
      ) : null}
    </div>
  )
}
