import { z } from "zod";

const currentVehicleYear = new Date().getFullYear();

export const vehicleFormSchema = z.object({
  category: z.string().trim().min(1, "Category is required."),
  make: z.string().trim().min(1, "Make is required."),
  model: z.string().trim().min(1, "Model is required."),
  year: z
    .string()
    .trim()
    .min(1, "Year is required.")
    .refine((value) => !Number.isNaN(Number(value)), "Year is required.")
    .refine(
      (value) => Number(value) >= 1995 && Number(value) <= currentVehicleYear + 1,
      `Year must be between 1995 and ${currentVehicleYear + 1}.`,
    ),
  transmission: z.enum(["AUTO", "MANUAL"]),
  fuelType: z.string(),
  seats: z
    .string()
    .trim()
    .min(1, "Seats is required.")
    .refine((value) => !Number.isNaN(Number(value)), "Seats is required.")
    .refine((value) => Number(value) > 0, "Seats must be greater than zero."),
  status: z.enum(["DRAFT", "ACTIVE", "MAINTENANCE", "SUSPENDED", "ARCHIVED"]),
  city: z.string().trim().min(1, "City is required."),
  plateNumber: z.string().trim().min(1, "Plate number is required."),
  vin: z.string(),
});

export type VehicleFormState = z.infer<typeof vehicleFormSchema>;

export const listingFormSchema = z.object({
  vehicleId: z.string().min(1, "Vehicle is required."),
  title: z.string().trim().min(1, "Title is required."),
  description: z.string().trim().min(1, "Description is required."),
  city: z.string().trim().min(1, "City is required."),
  address: z.string().trim().min(1, "Address is required."),
  basePricePerDay: z
    .string()
    .trim()
    .min(1, "Base price per day is required.")
    .refine((value) => !Number.isNaN(Number(value)), "Base price per day is required.")
    .refine((value) => Number(value) > 0, "Base price must be greater than zero."),
  dailyKmLimit: z
    .string()
    .trim()
    .min(1, "Daily km limit is required.")
    .refine((value) => !Number.isNaN(Number(value)), "Daily km limit is required.")
    .refine((value) => Number(value) > 0, "Daily km limit must be greater than zero."),
  instantBook: z.boolean(),
  cancellationPolicy: z.enum(["FLEXIBLE", "MODERATE", "STRICT"]),
});

export type HostListingFormState = z.infer<typeof listingFormSchema>;

export type AvailabilitySelectionState = {
  selectedDates: string[];
};
