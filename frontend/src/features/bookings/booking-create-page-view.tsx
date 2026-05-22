"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useRef, useState } from "react";
import { toast } from "sonner";

import { AppShell } from "@/components/rentflow/app-shell";
import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { PageHeader } from "@/components/rentflow/page-header";
import { createBooking, type CreateBookingInput } from "@/features/bookings/api";
import { getTodayIsoDate, validateBookingForm } from "@/features/bookings/date-utils";
import type {
  BookingCreateFormErrors,
  BookingCreateFormState,
} from "@/features/bookings/types";
import { getListingDetailById } from "@/features/listings/api";
import { ApiError } from "@/lib/api-error";
import { handleApiError } from "@/lib/handle-api-error";
import { newIdempotencyKey } from "@/lib/idempotency";

type BookingCreatePageViewProps = {
  listingId: string;
  isGuest: boolean;
};

export function BookingCreatePageView({ listingId, isGuest }: BookingCreatePageViewProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data: listing } = useQuery({
    queryKey: ["listings", listingId],
    queryFn: () => getListingDetailById(listingId),
  });
  const idempotencyKeyRef = useRef<string>(newIdempotencyKey());
  const [form, setForm] = useState<BookingCreateFormState>({
    pickupDate: "",
    returnDate: "",
    pickupLocation: "",
    returnLocation: "",
    selectedExtraIds: [],
  });
  const [errors, setErrors] = useState<BookingCreateFormErrors>({});
  const [overlap, setOverlap] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);

  const selectedExtras = useMemo(() => {
    if (!listing) return [];
    return listing.extras.filter((extra) => form.selectedExtraIds.includes(extra.id));
  }, [form.selectedExtraIds, listing]);

  const createMutation = useMutation({
    mutationFn: (input: CreateBookingInput) => createBooking(input, idempotencyKeyRef.current),
    onSuccess: (booking) => {
      queryClient.invalidateQueries({ queryKey: ["bookings", "me"] });
      queryClient.setQueryData(["bookings", booking.id], booking);
      router.push(`/bookings/${booking.id}`);
    },
    onError: (err: unknown) =>
      handleApiError(err, {
        onCode: {
          BOOKING_OVERLAP_CUSTOMER: (e) =>
            setOverlap(e.message || "Bạn đã có booking trùng thời gian."),
          LISTING_NOT_AVAILABLE: (e) =>
            setErrors((prev) => ({
              ...prev,
              form: e.message || "Xe không khả dụng cho ngày đã chọn.",
            })),
          IDEMPOTENCY_KEY_CONFLICT: () => {
            idempotencyKeyRef.current = newIdempotencyKey();
            toast.error("Yêu cầu đã thay đổi, vui lòng submit lại");
          },
        },
        onFieldError: (field, message) => {
          if (
            field === "pickupDate" ||
            field === "returnDate" ||
            field === "pickupLocation" ||
            field === "returnLocation"
          ) {
            setErrors((prev) => ({ ...prev, [field]: message }));
          }
        },
        onUnknown: (e) => setSubmitError(e),
        onNetwork: () => setSubmitError(ApiError.network()),
      }),
  });

  if (!listing) {
    return (
      <AppShell activePath="/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-3xl font-bold text-foreground">Không tìm thấy xe</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Listing này hiện không có trong mock data tạm thời (Phase 3 sẽ wire BE).
          </p>
        </section>
      </AppShell>
    );
  }
  const listingData = listing;

  function updateField(
    field: Exclude<keyof BookingCreateFormState, "selectedExtraIds">,
    value: string,
  ) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined, form: undefined }));
    setOverlap(null);
    setSubmitError(null);
  }

  function toggleExtra(extraId: string) {
    setForm((prev) => {
      if (prev.selectedExtraIds.includes(extraId)) {
        return { ...prev, selectedExtraIds: prev.selectedExtraIds.filter((id) => id !== extraId) };
      }
      return { ...prev, selectedExtraIds: [...prev.selectedExtraIds, extraId] };
    });
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (isGuest) {
      setErrors({ form: "Bạn cần đăng nhập trước khi tạo booking." });
      return;
    }

    const nextErrors = validateBookingForm(form);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setOverlap(null);
    setSubmitError(null);
    createMutation.mutate({
      listingId: listingData.id,
      pickupDate: form.pickupDate,
      returnDate: form.returnDate,
      pickupLocation: form.pickupLocation || undefined,
      returnLocation: form.returnLocation || undefined,
      selectedExtraIds: form.selectedExtraIds,
    });
  }

  return (
    <AppShell activePath="/listings">
      <div className="space-y-6">
        <PageHeader
          title={`Đặt ${listingData.title}`}
          description="Tạo HELD booking. Hold giữ 15 phút sẵn sàng cho thanh toán."
        />

        {isGuest ? (
          <section className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-900">
            <p className="text-sm">
              Chế độ khách: cần đăng nhập để tạo booking.
            </p>
            <Link
              href={`/login?next=/listings/${listingData.id}/book`}
              className="mt-3 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
            >
              Đăng nhập để đặt xe
            </Link>
          </section>
        ) : null}

        {overlap ? (
          <section className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-900">
            <p className="text-sm font-semibold">Trùng booking</p>
            <p className="mt-1 text-sm">{overlap}</p>
            <Link
              href="/me/bookings"
              className="mt-3 inline-flex rounded-full bg-rose-700 px-4 py-2 text-xs font-semibold text-white transition-opacity hover:opacity-90"
            >
              Xem các booking đang active của bạn
            </Link>
          </section>
        ) : null}

        {submitError ? <ApiErrorPanel error={submitError} /> : null}

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <h2 className="text-lg font-bold text-foreground">Thông tin đặt xe</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Giá cơ bản: {listingData.basePricePerDay.toLocaleString("en-US")} {listingData.currency} / ngày
          </p>

          <form onSubmit={handleSubmit} noValidate className="mt-4 space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Ngày nhận</label>
                <input
                  type="date"
                  value={form.pickupDate}
                  min={getTodayIsoDate()}
                  onChange={(event) => updateField("pickupDate", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.pickupDate ? (
                  <p className="mt-1 text-xs text-rose-700">{errors.pickupDate}</p>
                ) : null}
              </div>
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Ngày trả</label>
                <input
                  type="date"
                  value={form.returnDate}
                  min={form.pickupDate || getTodayIsoDate()}
                  onChange={(event) => updateField("returnDate", event.target.value)}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.returnDate ? (
                  <p className="mt-1 text-xs text-rose-700">{errors.returnDate}</p>
                ) : null}
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Địa điểm nhận</label>
                <input
                  type="text"
                  value={form.pickupLocation}
                  onChange={(event) => updateField("pickupLocation", event.target.value)}
                  placeholder={listingData.address}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-semibold text-foreground">Địa điểm trả</label>
                <input
                  type="text"
                  value={form.returnLocation}
                  onChange={(event) => updateField("returnLocation", event.target.value)}
                  placeholder={listingData.address}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
              </div>
            </div>

            <div>
              <p className="mb-2 text-sm font-semibold text-foreground">Dịch vụ thêm</p>
              <div className="grid gap-2 sm:grid-cols-2">
                {listingData.extras.map((extra) => {
                  const checked = form.selectedExtraIds.includes(extra.id);
                  return (
                    <label
                      key={extra.id}
                      className="flex items-center justify-between rounded-lg border border-border bg-background px-3 py-2 text-sm"
                    >
                      <span>{extra.name}</span>
                      <span className="flex items-center gap-2">
                        <span className="text-muted-foreground">
                          {extra.price.toLocaleString("en-US")} {extra.currency}
                        </span>
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggleExtra(extra.id)}
                          className="size-4 rounded border-input"
                        />
                      </span>
                    </label>
                  );
                })}
              </div>
            </div>

            {selectedExtras.length > 0 ? (
              <p className="text-xs text-muted-foreground">
                Đã chọn {selectedExtras.length} dịch vụ thêm (mỗi loại số lượng 1).
              </p>
            ) : null}

            {errors.form ? (
              <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
                {errors.form}
              </p>
            ) : null}

            <div className="flex flex-wrap gap-2">
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="rounded-full bg-secondary px-5 py-2.5 text-sm font-semibold text-secondary-foreground transition-opacity hover:opacity-90 disabled:opacity-50"
              >
                {createMutation.isPending ? "Đang giữ xe..." : "Giữ xe này"}
              </button>
              <Link
                href={`/listings/${listingData.id}`}
                className="rounded-full border border-border bg-background px-5 py-2.5 text-sm font-semibold text-foreground hover:bg-accent"
              >
                Quay lại
              </Link>
            </div>
          </form>
        </section>
      </div>
    </AppShell>
  );
}
