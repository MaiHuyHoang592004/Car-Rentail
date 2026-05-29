"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useCallback, useRef, useState } from "react";
import { toast } from "sonner";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { getBookingById } from "@/features/bookings/api";
import type { BookingDetailViewModel } from "@/features/bookings/types";
import {
  authorizeBookingPayment,
  getBookingPayment,
  listPaymentBanks,
} from "@/features/payments/api";
import type { PaymentBank, PaymentDetail } from "@/features/payments/types";
import { ApiError } from "@/lib/api-error";
import { newIdempotencyKey } from "@/lib/idempotency";

/* ------------------------------------------------------------------ */
/*  Constants                                                         */
/* ------------------------------------------------------------------ */

const PAYABLE_STATUSES = ["HELD", "PENDING_HOST_APPROVAL", "CONFIRMED"] as const;

/* ------------------------------------------------------------------ */
/*  Helpers                                                           */
/* ------------------------------------------------------------------ */

function statusLabel(status: string): string {
  switch (status) {
    case "UNPAID":
      return "Chưa thanh toán";
    case "AUTHORIZED":
      return "Đã giữ tiền (authorized)";
    case "CAPTURED":
      return "Đã thanh toán";
    case "PARTIALLY_REFUNDED":
      return "Hoàn tiền một phần";
    case "REFUNDED":
      return "Đã hoàn tiền";
    case "VOIDED":
      return "Đã hủy thanh toán";
    case "FAILED":
      return "Thanh toán thất bại";
    default:
      return status;
  }
}

function paymentStatusBadge(status: string): string {
  if (status === "AUTHORIZED") return "bg-emerald-100 text-emerald-800 border-emerald-200";
  if (status === "CAPTURED") return "bg-blue-100 text-blue-800 border-blue-200";
  if (status === "FAILED") return "bg-rose-100 text-rose-800 border-rose-200";
  if (status === "VOIDED") return "bg-gray-100 text-gray-600 border-gray-200";
  return "bg-amber-100 text-amber-800 border-amber-200";
}

function providerLabel(provider: string | null): string {
  if (provider === "COREBANK") return "CoreBank Demo";
  if (provider === "VIETQR_MANUAL") return "Chuyển khoản / VietQR";
  return provider ?? "—";
}

/* ------------------------------------------------------------------ */
/*  Component                                                         */
/* ------------------------------------------------------------------ */

type BookingPaymentPageViewProps = {
  bookingId: string;
};

export function BookingPaymentPageView({ bookingId }: BookingPaymentPageViewProps) {
  const queryClient = useQueryClient();
  const idempotencyKeyRef = useRef<string | null>(null);
  const [selectedBankId, setSelectedBankId] = useState<string | null>(null);

  /* ----- queries ----- */
  const bookingQuery = useQuery({
    queryKey: ["bookings", bookingId],
    queryFn: () => getBookingById(bookingId),
    retry: false,
  });

  const paymentQuery = useQuery({
    queryKey: ["bookings", bookingId, "payment"],
    queryFn: () => getBookingPayment(bookingId),
    retry: false,
  });

  const banksQuery = useQuery({
    queryKey: ["payments", "banks"],
    queryFn: () => listPaymentBanks(),
  });

  /* ----- authorize mutation ----- */
  const authorizeMutation = useMutation({
    mutationFn: (params: { bankId: string; paymentMethod: string }) =>
      authorizeBookingPayment(
        bookingId,
        { bankId: params.bankId, paymentMethod: params.paymentMethod as "COREBANK_TRANSFER" | "BANK_TRANSFER_QR" },
        idempotencyKeyRef.current!,
      ),
    onSuccess: (result) => {
      toast.success("Thanh toán đã được xử lý");
      queryClient.setQueryData(["bookings", bookingId, "payment"], {
        booking: {
          id: result.booking.id,
          customerId: "",
          hostId: "",
          status: result.booking.status,
          pickupDate: result.booking.pickupDate,
          returnDate: result.booking.returnDate,
        },
        payment: {
          id: result.payment.id,
          selectedBankId,
          paymentMethod: result.payment.paymentMethod,
          provider: result.payment.provider,
          status: result.payment.status,
          authorizedAmount: result.payment.authorizedAmount,
          capturedAmount: result.payment.capturedAmount,
          refundedAmount: result.payment.refundedAmount,
          currency: result.payment.currency,
          externalOrderRef: result.payment.externalOrderRef,
          providerPaymentOrderId: result.payment.providerPaymentOrderId,
          providerHoldId: result.payment.providerHoldId,
          providerStatus: null,
          transferInstruction: result.payment.transferInstruction,
        },
        transactions: [],
      } as PaymentDetail);
      queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
      idempotencyKeyRef.current = null;
    },
    onError: (error: unknown) => {
      if (error instanceof ApiError) {
        if (error.code === "BOOKING_INVALID_STATUS") {
          queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
          toast.error("Booking không ở trạng thái có thể thanh toán. Đang tải lại...");
        } else if (error.code === "EMAIL_NOT_VERIFIED") {
          toast.error("Vui lòng xác minh email trước khi thanh toán.");
        } else {
          toast.error(error.message || "Thanh toán thất bại");
        }
      } else {
        toast.error("Thanh toán thất bại");
      }
      idempotencyKeyRef.current = null;
    },
  });

  /* ----- handlers ----- */
  const handleAuthorize = useCallback(
    (bank: PaymentBank) => {
      if (!idempotencyKeyRef.current) {
        idempotencyKeyRef.current = newIdempotencyKey();
      }
      setSelectedBankId(bank.id);
      authorizeMutation.mutate({
        bankId: bank.id,
        paymentMethod: bank.paymentMethod,
      });
    },
    [authorizeMutation],
  );

  /* ----- loading / error ----- */
  if (bookingQuery.isLoading) {
    return (
      <AppShell activePath="/me/bookings">
        <PageSkeleton message="Đang tải thông tin booking..." />
      </AppShell>
    );
  }

  if (bookingQuery.isError || !bookingQuery.data) {
    return (
      <AppShell activePath="/me/bookings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">
            Không tìm thấy booking hoặc bạn không có quyền truy cập.
          </p>
          <Link
            href="/me/bookings"
            className="mt-4 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90"
          >
            Quay về danh sách
          </Link>
        </section>
      </AppShell>
    );
  }

  const booking: BookingDetailViewModel = bookingQuery.data;
  const payment: PaymentDetail | null | undefined = paymentQuery.data;
  const banks: PaymentBank[] = banksQuery.data ?? [];
  const isPayable = (PAYABLE_STATUSES as readonly string[]).includes(booking.status);
  const hasPayment = payment && payment.payment;
  const isAuthorized = hasPayment && payment!.payment.status === "AUTHORIZED";
  const isActiveAuthorize = authorizeMutation.isPending;

  return (
    <AppShell activePath="/me/bookings">
      <div className="space-y-6">
        <PageHeader
          title={`Thanh toán · ${booking.listingTitle}`}
          description={`${booking.pickupDate} → ${booking.returnDate}`}
          actions={
            <Link
              href={`/bookings/${bookingId}`}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Quay về booking
            </Link>
          }
        />

        {/* Payment status panel */}
        {hasPayment && (
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">
              Trạng thái thanh toán
            </p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <span
                className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-[11px] font-semibold ${paymentStatusBadge(payment!.payment.status)}`}
              >
                {statusLabel(payment!.payment.status)}
              </span>
              {payment!.payment.provider && (
                <span className="inline-flex items-center rounded-full border border-border bg-background px-2 py-0.5 text-[10px] font-semibold text-foreground">
                  {providerLabel(payment!.payment.provider)}
                </span>
              )}
            </div>

            <div className="mt-4 grid gap-3 sm:grid-cols-3">
              <InfoBlock label="Số tiền giữ (authorized)">
                {payment!.payment.authorizedAmount.toLocaleString("en-US")} {payment!.payment.currency}
              </InfoBlock>
              <InfoBlock label="Đã thanh toán (captured)">
                {payment!.payment.capturedAmount.toLocaleString("en-US")} {payment!.payment.currency}
              </InfoBlock>
              <InfoBlock label="Đã hoàn (refunded)">
                {payment!.payment.refundedAmount.toLocaleString("en-US")} {payment!.payment.currency}
              </InfoBlock>
            </div>

            {/* Transfer instruction */}
            {payment!.payment.transferInstruction && (
              <div className="mt-4 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                <p className="font-semibold">Thông tin chuyển khoản</p>
                <div className="mt-2 space-y-1 text-xs">
                  <p>Ngân hàng: {payment!.payment.transferInstruction.bankCode} ({payment!.payment.transferInstruction.bankBin})</p>
                  <p>Số tài khoản: {payment!.payment.transferInstruction.accountNumber}</p>
                  <p>Chủ tài khoản: {payment!.payment.transferInstruction.accountName}</p>
                  <p>Số tiền: {payment!.payment.transferInstruction.amount.toLocaleString("en-US")} VND</p>
                  <p>Nội dung: {payment!.payment.transferInstruction.content}</p>
                </div>
                <p className="mt-2 text-xs italic">
                  Sau khi chuyển khoản thành công, hệ thống sẽ tự động xác nhận.
                </p>
              </div>
            )}
          </section>
        )}

        {/* Bank selector + authorize */}
        {isPayable && !isAuthorized && (
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-bold text-foreground">Chọn ngân hàng thanh toán</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              Chọn ngân hàng để thực hiện thanh toán cho booking này.
            </p>

            {banksQuery.isLoading ? (
              <p className="mt-3 text-sm text-muted-foreground">Đang tải danh sách ngân hàng...</p>
            ) : banks.length === 0 ? (
              <p className="mt-3 text-sm text-muted-foreground">
                Hiện chưa có ngân hàng nào khả dụng.
              </p>
            ) : (
              <div className="mt-4 space-y-2">
                {banks
                  .filter((b) => b.active)
                  .map((bank) => (
                    <div
                      key={bank.id}
                      className="flex items-center justify-between rounded-lg border border-border bg-background px-4 py-3"
                    >
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-foreground">{bank.shortName}</p>
                        <p className="text-xs text-muted-foreground">
                          {bank.fullName}
                          {bank.bin && ` · BIN ${bank.bin}`}
                        </p>
                        <p className="mt-0.5 text-[10px] text-muted-foreground">
                          {providerLabel(bank.provider)} · {bank.paymentMethod}
                        </p>
                      </div>
                      <button
                        type="button"
                        disabled={isActiveAuthorize}
                        onClick={() => handleAuthorize(bank)}
                        className="shrink-0 rounded-full bg-primary px-4 py-1.5 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {isActiveAuthorize && selectedBankId === bank.id
                          ? "Đang xử lý..."
                          : "Thanh toán"}
                      </button>
                    </div>
                  ))}
              </div>
            )}
          </section>
        )}

        {/* Already authorized message */}
        {isAuthorized && (
          <section className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            <p className="font-semibold">Thanh toán đã được giữ (authorized)</p>
            <p className="mt-1 text-xs">
              Booking đang ở trạng thái <strong>{booking.status}</strong>.
              {booking.status === "PENDING_HOST_APPROVAL"
                ? " Đang chờ chủ xe xác nhận."
                : booking.status === "CONFIRMED"
                  ? " Booking đã xác nhận."
                  : ""}
            </p>
          </section>
        )}

        {/* Non-payable status notice */}
        {!isPayable && (
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <p className="text-sm text-muted-foreground">
              Booking đang ở trạng thái <strong>{booking.status}</strong>, không thể thanh toán từ giao diện này.
            </p>
          </section>
        )}
      </div>
    </AppShell>
  );
}

/* ------------------------------------------------------------------ */
/*  Tiny helper                                                       */
/* ------------------------------------------------------------------ */

function InfoBlock({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-border bg-background px-3 py-2">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-1 text-sm font-semibold text-foreground">{children}</p>
    </div>
  );
}