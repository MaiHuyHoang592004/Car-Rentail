import { z } from "zod"
""
const currentYear = new Date().getFullYear()
""
export const vehicleFormSchema = z.object({
  category: z.string().trim().min(1, "Loai xe khong duoc de trong."),
  make: z.string().trim().min(1, "Hang xe khong duoc de trong."),
  model: z.string().trim().min(1, "Dong xe khong duoc de trong."),
  year: z
    .string()
    .trim()
    .min(1, "Nam san xuat khong duoc de trong.")
    .refine((v) => !Number.isNaN(Number(v)), "Nam san xuat khong hop le.")
    .refine(
      (v) => Number(v) >= 1995 && Number(v) <= currentYear + 1,
      `Nam phai tu 1995 den ${currentYear + 1}.`, 
    ),
  transmission: z.enum(["AUTO", "MANUAL"]),
  fuelType: z.string(),
  seats: z
    .string()
    .trim()
    .min(1, "So ghe khong duoc de trong.")
    .refine((v) => !Number.isNaN(Number(v)), "So ghe khong hop le.")
    .refine((v) => Number(v) > 0, "So ghe phai lon hon 0."),
  status: z.enum(["DRAFT", "ACTIVE", "MAINTENANCE", "SUSPENDED", "ARCHIVED"]),
  city: z.string().trim().min(1, "Thanh pho khong duoc de trong."),
  plateNumber: z.string().trim().min(1, "Bien so khong duoc de trong."),
  vin: z.string(),
});
""
export type VehicleFormState = z.infer<typeof vehicleFormSchema>
""
export const listingFormSchema = z.object({
  vehicleId: z.string().min(1, "Vui long chon xe."),
  title: z.string().trim().min(1, "Tieu de khong duoc de trong."),
  description: z.string().trim().min(1, "Mo ta khong duoc de trong."),
  city: z.string().trim().min(1, "Thanh pho khong duoc de trong."),
  address: z.string().trim().min(1, "Dia chi khong duoc de trong."),
  basePricePerDay: z
    .string()
    .trim()
    .min(1, "Gia theo ngay khong duoc de trong.")
    .refine((v) => !Number.isNaN(Number(v)), "Gia theo ngay khong hop le.")
    .refine((v) => Number(v) > 0, "Gia phai lon hon 0."),
  dailyKmLimit: z
    .string()
    .trim()
    .min(1, "Gioi han km/ngay khong duoc de trong.")
    .refine((v) => !Number.isNaN(Number(v)), "Gioi han km/ngay khong hop le.")
    .refine((v) => Number(v) > 0, "Gioi han km/ngay phai lon hon 0."),
  instantBook: z.boolean(),
  cancellationPolicy: z.enum(["FLEXIBLE", "MODERATE", "STRICT"]),
});
""
export type HostListingFormState = z.infer<typeof listingFormSchema>
""
export type AvailabilitySelectionState = {
  selectedDates: string[];
};
