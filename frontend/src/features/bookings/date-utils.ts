import type { BookingCreateFormErrors } from "@/features/bookings/types";

type DateRangeInput = {
  pickupDate: string;
  returnDate: string;
};

const DAY_IN_MS = 24 * 60 * 60 * 1000;
const MAX_RENTAL_DAYS = 30;
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export function getTodayIsoDate(now: Date = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function isoToUtcMs(iso: string): number {
  const [y, m, d] = iso.split("-").map(Number);
  return Date.UTC(y, m - 1, d);
}

function isValidIsoDate(iso: string): boolean {
  if (!ISO_DATE_PATTERN.test(iso)) return false;
  const [y, m, d] = iso.split("-").map(Number);
  if (m < 1 || m > 12 || d < 1 || d > 31) return false;
  const utc = Date.UTC(y, m - 1, d);
  const date = new Date(utc);
  return (
    date.getUTCFullYear() === y &&
    date.getUTCMonth() === m - 1 &&
    date.getUTCDate() === d
  );
}

export function validateBookingForm(
  form: DateRangeInput,
  todayIso: string = getTodayIsoDate(),
): BookingCreateFormErrors {
  const errors: BookingCreateFormErrors = {};

  if (!form.pickupDate) {
    errors.pickupDate = "Vui lòng chọn ngày nhận xe.";
  }
  if (!form.returnDate) {
    errors.returnDate = "Vui lòng chọn ngày trả xe.";
  }
  if (!form.pickupDate || !form.returnDate) {
    return errors;
  }

  if (!isValidIsoDate(form.pickupDate) || !isValidIsoDate(form.returnDate)) {
    errors.form = "Định dạng ngày không hợp lệ.";
    return errors;
  }

  if (form.pickupDate < todayIso) {
    errors.pickupDate = "Ngày nhận không thể ở quá khứ.";
  }

  if (form.returnDate <= form.pickupDate) {
    errors.returnDate = "Ngày trả phải sau ngày nhận.";
    return errors;
  }

  const rentalDays =
    (isoToUtcMs(form.returnDate) - isoToUtcMs(form.pickupDate)) / DAY_IN_MS;
  if (rentalDays > MAX_RENTAL_DAYS) {
    errors.returnDate = `Thời gian thuê tối đa ${MAX_RENTAL_DAYS} ngày.`;
  }

  return errors;
}
