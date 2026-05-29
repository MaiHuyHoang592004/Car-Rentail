"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { CalendarDays, MapPin, PlusCircle } from "lucide-react";

import { AppShell } from "@/components/rentflow/app-shell";
import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { BookingSectionCard } from "@/features/bookings/booking-section-card";
import { BookingPriceSummary } from "@/features/bookings/booking-price-summary";
import { createBooking, type CreateBookingInput } from "@/features/bookings/api";
import { getTodayIsoDate } from "@/features/bookings/date-utils";
import { bookingCreateSchema, type BookingCreateFormState } from "@/features/bookings/forms";
import { getListingDetailById } from "@/features/listings/api";
import { ApiError } from "@/lib/api-error";
import { handleApiError } from "@/lib/handle-api-error";
import { newIdempotencyKey } from "@/lib/idempotency";

type VerificationGateState = {
  message: string;
};

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
  const form = useForm<BookingCreateFormState>({
    resolver: zodResolver(bookingCreateSchema),
    defaultValues: {
      pickupDate: "",
      returnDate: "",
      pickupLocation: "",
      returnLocation: "",
      selectedExtraIds: [],
    },
  });
  const [overlap, setOverlap] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const [verificationGate, setVerificationGate] = useState<VerificationGateState | null>(null);
  const selectedExtraIds = form.watch("selectedExtraIds");
  const pickupDate = form.watch("pickupDate");
  const returnDate = form.watch("returnDate");
  const errors = form.formState.errors;
  const selectedExtras = useMemo(() => {
    if (!listing) return [];
    return listing.extras.filter((extra) => selectedExtraIds.includes(extra.id));
  }, [listing, selectedExtraIds]);
  const listingData = listing!;

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
          EMAIL_NOT_VERIFIED: (e) =>
            setVerificationGate({
              message: e.message || "Ban can xac minh email truoc khi dat xe.",
            }),
          BOOKING_OVERLAP_CUSTOMER: (e) =>
            setOverlap(e.message || "Ban da co booking trung thoi gian."),
          LISTING_NOT_AVAILABLE: (e) =>
            form.setError("root", {
              message: e.message || "Xe khong kha dung cho ngay da chon.",
            }),
          IDEMPOTENCY_KEY_CONFLICT: () => {
            idempotencyKeyRef.current = newIdempotencyKey();
            toast.error("Yeu cau da thay doi, vui long submit lai");
          },
        },
        onFieldError: (field, message) => {
          if (
            field === "pickupDate" ||
            field === "returnDate" ||
            field === "pickupLocation" ||
            field === "returnLocation"
          ) {
            form.setError(field, { message });
          }
        },
        onUnknown: (e) => setSubmitError(e),
        onNetwork: () => setSubmitError(ApiError.network()),
      }),
  });

  function toggleExtra(extraId: string) {
    const next = selectedExtraIds.includes(extraId)
      ? selectedExtraIds.filter((id) => id !== extraId)
      : [...selectedExtraIds, extraId];
    form.setValue("selectedExtraIds", next, { shouldDirty: true });
  }

  function handleSubmit(values: BookingCreateFormState) {
    if (isGuest) {
      form.setError("root", { message: "Ban can dang nhap truoc khi tao booking." });
      return;
    }
    setOverlap(null);
    setSubmitError(null);
    setVerificationGate(null);
    createMutation.mutate({
      listingId: listingData.id,
      pickupDate: values.pickupDate,
      returnDate: values.returnDate,
      pickupLocation: values.pickupLocation || undefined,
      returnLocation: values.returnLocation || undefined,
      selectedExtraIds: values.selectedExtraIds,
    });
  }

  if (!listing) {
    return (
      <AppShell activePath="/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-2xl font-bold text-foreground">Khong tim thay xe</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Xe nay hien khong kha dung hoac da bi go khoi he thong.
          </p>
        </section>
      </AppShell>
    );
  }

  return (
    <AppShell activePath="/listings">
      <div className="space-y-5">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold text-foreground">Dat xe</h1>
          <Link
            href={`/listings/${listingData.id}`}
            className="flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
          >
            Quay lai
          </Link>
        </div>

        {isGuest ? (
          <section className="rounded-xl border border-amber-200 bg-amber-50 p-4">
            <p className="text-sm text-amber-900">Ban can dang nhap de hoan tat dat xe.</p>
            <Link
              href={`/login?next=/listings/${listingData.id}/book`}
              className="mt-3 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90"
            >
              Dang nhap de dat xe
            </Link>
          </section>
        ) : null}

        {overlap ? (
          <section className="rounded-xl border border-rose-200 bg-rose-50 p-4">
            <p className="text-sm font-semibold text-rose-900">Trung booking</p>
            <p className="mt-1 text-sm text-rose-800">{overlap}</p>
            <Link
              href="/me/bookings"
              className="mt-3 inline-flex rounded-full bg-rose-700 px-4 py-2 text-xs font-semibold text-white hover:opacity-90"
            >
              Xem cac booking cua ban
            </Link>
          </section>
        ) : null}

        {verificationGate ? (
          <section className="rounded-xl border border-amber-200 bg-amber-50 p-4">
            <p className="text-sm font-semibold text-amber-900">Email chua duoc xac minh</p>
            <p className="mt-1 text-sm text-amber-800">{verificationGate.message}</p>
            <Link
              href="/me/profile"
              className="mt-3 inline-flex rounded-full bg-amber-700 px-4 py-2 text-xs font-semibold text-white hover:opacity-90"
            >
              Xac minh email
            </Link>
          </section>
        ) : null}

        {submitError ? <ApiErrorPanel error={submitError} /> : null}

        <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
          <div className="min-w-0 flex-1 space-y-5">
            <BookingSectionCard
              title="Thoi gian thue"
              icon={<CalendarDays className="h-5 w-5" />}
            >
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-foreground">
                    Ngay nhan xe
                  </label>
                  <input
                    type="date"
                    {...form.register("pickupDate", {
                      onChange: () => {
                        setOverlap(null);
                        setSubmitError(null);
                      },
                    })}
                    min={getTodayIsoDate()}
                    className="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                  />
                  {errors.pickupDate ? (
                    <p className="mt-1 text-xs text-rose-700">{errors.pickupDate.message}</p>
                  ) : null}
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-foreground">
                    Ngay tra xe
                  </label>
                  <input
                    type="date"
                    {...form.register("returnDate", {
                      onChange: () => {
                        setOverlap(null);
                        setSubmitError(null);
                      },
                    })}
                    min={pickupDate || getTodayIsoDate()}
                    className="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                  />
                  {errors.returnDate ? (
                    <p className="mt-1 text-xs text-rose-700">{errors.returnDate.message}</p>
                  ) : null}
                </div>
              </div>
            </BookingSectionCard>

            <BookingSectionCard
              title="Dia diem nhan / tra xe"
              icon={<MapPin className="h-5 w-5" />}
            >
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-foreground">
                    Dia diem nhan xe
                  </label>
                  <input
                    type="text"
                    {...form.register("pickupLocation")}
                    placeholder={listingData.address}
                    className="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground placeholder:text-muted-foreground outline-none ring-primary/30 focus:ring-2"
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-foreground">
                    Dia diem tra xe
                  </label>
                  <input
                    type="text"
                    {...form.register("returnLocation")}
                    placeholder={listingData.address}
                    className="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground placeholder:text-muted-foreground outline-none ring-primary/30 focus:ring-2"
                  />
                </div>
              </div>
            </BookingSectionCard>

            {listingData.extras.length > 0 ? (
              <BookingSectionCard
                title="Dich vu them"
                icon={<PlusCircle className="h-5 w-5" />}
              >
                <div className="flex flex-col gap-2">
                  {listingData.extras.map((extra) => {
                    const checked = selectedExtraIds.includes(extra.id);
                    return (
                      <label
                        key={extra.id}
                        className={`flex items-center justify-between rounded-lg border px-4 py-3 cursor-pointer transition-colors ${
                          checked
                            ? "border-primary bg-primary/5"
                            : "border-border bg-background hover:border-primary/50"
                        }`}
                      >
                        <div className="flex items-center gap-3">
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggleExtra(extra.id)}
                            className="size-4 rounded border-input accent-primary"
                          />
                          <span className="text-sm font-medium text-foreground">
                            {extra.name}
                          </span>
                        </div>
                        <span className="text-sm font-semibold text-foreground">
                          {extra.price.toLocaleString("vi-VN")} {extra.currency}
                        </span>
                      </label>
                    );
                  })}
                </div>
                {selectedExtras.length > 0 ? (
                  <p className="mt-2 text-xs text-muted-foreground">
                    Da chon {selectedExtras.length} dich vu them.
                  </p>
                ) : null}
              </BookingSectionCard>
            ) : null}

            {errors.root ? (
              <p className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
                {errors.root.message}
              </p>
            ) : null}
          </div>

          <div className="w-full lg:sticky lg:top-6 lg:w-80 lg:shrink-0">
            <BookingPriceSummary
              listing={listingData}
              pickupDate={pickupDate}
              returnDate={returnDate}
              selectedExtraIds={selectedExtraIds}
              onBook={form.handleSubmit(handleSubmit)}
              isPending={createMutation.isPending}
            />
          </div>
        </div>
      </div>
    </AppShell>
  );
}
