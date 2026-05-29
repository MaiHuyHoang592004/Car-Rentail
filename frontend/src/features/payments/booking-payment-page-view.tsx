"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useCallback, useRef, useState } from "react";
import { toast } from "sonner";
import { ChevronDown, ChevronUp, Clock, Copy, CreditCard, ShieldCheck } from "lucide-react";

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
import { getPaymentMethodLabel } from "@/lib/display-labels";
import { formatMoney } from "@/lib/formatters";
import { newIdempotencyKey } from "@/lib/idempotency";

const PAYABLE_STATUSES = ["HELD", "PENDING_HOST_APPROVAL", "CONFIRMED"] as const;

function PaymentStatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    AUTHORIZED: "bg-emerald-100 text-emerald-800",
    CAPTURED: "bg-blue-100 text-blue-800",
    PARTIALLY_REFUNDED: "bg-amber-100 text-amber-800",
    REFUNDED: "bg-zinc-100 text-zinc-700",
    VOIDED: "bg-zinc-100 text-zinc-700",
    FAILED: "bg-rose-100 text-rose-800",
  };
  const style = styles[status] ?? "bg-amber-100 text-amber-800";
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      {status}
    </span>
  );
}

type BookingPaymentPageViewProps = {
  bookingId: string;
};

export function BookingPaymentPageView({ bookingId }: BookingPaymentPageViewProps) {
  const queryClient = useQueryClient();
  const idempotencyKeyRef = useRef<string | null>(null);
  const [selectedBankId, setSelectedBankId] = useState<string | null>(null);
  const [techDetailsOpen, setTechDetailsOpen] = useState(false);

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

  const authorizeMutation = useMutation({
    mutationFn: (params: { bankId: string; paymentMethod: string }) =>
      authorizeBookingPayment(
        bookingId,
        {
          bankId: params.bankId,
          paymentMethod: params.paymentMethod as "COREBANK_TRANSFER" | "BANK_TRANSFER_QR",
        },
        idempotencyKeyRef.current!,
      ),
    onSuccess: (result) => {
      toast.success("Thanh toan da duoc gui");
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
          toast.error("Booking khong o trang thai thanh toan. Dang tai lai...");
        } else if (error.code === "EMAIL_NOT_VERIFIED") {
          toast.error("Vui long xac minh email truoc khi thanh toan.");
        } else {
          toast.error(error.message || "Thanh toan that bai");
        }
      } else {
        toast.error("Thanh toan that bai");
      }
      idempotencyKeyRef.current = null;
    },
  });

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

  if (bookingQuery.isLoading) {
    return (
      <AppShell activePath="/me/bookings">
        <PageSkeleton message="Dang tai thong tin..." />
      </AppShell>
    );
  }

  if (bookingQuery.isError || !bookingQuery.data) {
    return (
      <AppShell activePath="/me/bookings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">
            Khong tim thay booking hoac ban khong co quyen truy cap.
          </p>
          <Link
            href="/me/bookings"
            className="mt-4 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90"
          >
            Quay ve danh sach
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
  const isPending = authorizeMutation.isPending;
  const isCaptured = hasPayment && payment!.payment.status === "CAPTURED";
  const isFailed = hasPayment && payment!.payment.status === "FAILED";
  const isRefundOrVoid =
    hasPayment &&
    (payment!.payment.status === "REFUNDED" ||
      payment!.payment.status === "PARTIALLY_REFUNDED" ||
      payment!.payment.status === "VOIDED");

  return (
    <AppShell activePath="/me/bookings">
      <div className="space-y-6">
        <PageHeader
          title="Thanh toan"
          description={booking.listingTitle}
          actions={
            <Link
              href={`/bookings/${bookingId}`}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Quay ve
            </Link>
          }
        />

        <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
          {/* Left column: status + bank selection + transfer */}
          <div className="min-w-0 flex-1 space-y-5">
            {/* Payment status card */}
            {hasPayment ? (
              <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                      Trang thai thanh toan
                    </p>
                    <div className="mt-1.5 flex items-center gap-2">
                      <PaymentStatusBadge status={payment!.payment.status} />
                      {payment!.payment.provider ? (
                        <span className="text-xs text-muted-foreground">
                          {getPaymentMethodLabel(payment!.payment.provider)}
                        </span>
                      ) : null}
                    </div>
                  </div>
                  {isAuthorized || isCaptured ? (
                    <ShieldCheck className="h-8 w-8 text-emerald-600" />
                  ) : isFailed ? (
                    <Clock className="h-8 w-8 text-rose-600" />
                  ) : null}
                </div>

                {/* Amount summary */}
                <div className="mt-4 grid gap-3 sm:grid-cols-3">
                  <div className="rounded-lg border border-border bg-background px-3 py-2">
                    <p className="text-xs uppercase tracking-wide text-muted-foreground">
                      So tien giu
                    </p>
                    <p className="mt-1 text-sm font-semibold text-foreground">
                      {formatMoney(payment!.payment.authorizedAmount, payment!.payment.currency)}
                    </p>
                  </div>
                  <div className="rounded-lg border border-border bg-background px-3 py-2">
                    <p className="text-xs uppercase tracking-wide text-muted-foreground">
                      Da thanh toan
                    </p>
                    <p className="mt-1 text-sm font-semibold text-foreground">
                      {formatMoney(payment!.payment.capturedAmount, payment!.payment.currency)}
                    </p>
                  </div>
                  <div className="rounded-lg border border-border bg-background px-3 py-2">
                    <p className="text-xs uppercase tracking-wide text-muted-foreground">
                      Da hoan
                    </p>
                    <p className="mt-1 text-sm font-semibold text-foreground">
                      {formatMoney(payment!.payment.refundedAmount, payment!.payment.currency)}
                    </p>
                  </div>
                </div>

                {/* Transfer instruction */}
                {payment!.payment.transferInstruction ? (
                  <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-4">
                    <div className="flex items-center gap-2">
                      <CreditCard className="h-4 w-4 text-amber-700" />
                      <p className="text-sm font-semibold text-amber-900">Thong tin chuyen khoan</p>
                    </div>
                    <div className="mt-3 space-y-1.5 text-sm text-amber-900">
                      <div className="flex justify-between">
                        <span>Ngan hang</span>
                        <span className="font-medium">
                          {payment!.payment.transferInstruction.bankCode} ({payment!.payment.transferInstruction.bankBin})
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span>So tai khoan</span>
                        <div className="flex items-center gap-1.5">
                          <span className="font-medium">{payment!.payment.transferInstruction.accountNumber}</span>
                          <CopyButton text={payment!.payment.transferInstruction.accountNumber} />
                        </div>
                      </div>
                      <div className="flex justify-between">
                        <span>Chu tai khoan</span>
                        <span className="font-medium">{payment!.payment.transferInstruction.accountName}</span>
                      </div>
                      <div className="flex justify-between">
                        <span>So tien</span>
                        <span className="font-medium">
                          {formatMoney(payment!.payment.transferInstruction.amount, payment!.payment.currency)}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span>Noi dung</span>
                        <div className="flex items-center gap-1.5">
                          <span className="font-medium">{payment!.payment.transferInstruction.content}</span>
                          <CopyButton text={payment!.payment.transferInstruction.content} />
                        </div>
                      </div>
                    </div>
                    <p className="mt-3 border-t border-amber-200 pt-3 text-xs italic text-amber-800">
                      Sau khi chuyen khoan thanh cong, he thong se tu dong xac nhan.
                    </p>
                  </div>
                ) : null}
              </section>
            ) : null}

            {/* Bank selection */}
            {isPayable && !isAuthorized && !isCaptured && (
              <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
                <h2 className="text-base font-bold text-foreground">Chon phuong thuc thanh toan</h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  Chon ngan hang hoac phuong thuc de thanh toan.
                </p>

                {banksQuery.isLoading ? (
                  <p className="mt-3 text-sm text-muted-foreground">Dang tai danh sach ngan hang...</p>
                ) : banks.filter((b) => b.active).length === 0 ? (
                  <p className="mt-3 text-sm text-muted-foreground">
                    Hien chua co ngan hang nao kha dung.
                  </p>
                ) : (
                  <div className="mt-4 space-y-2">
                    {banks
                      .filter((b) => b.active)
                      .map((bank) => (
                        <div
                          key={bank.id}
                          className={`flex items-center justify-between rounded-lg border px-4 py-3 transition-colors ${
                            selectedBankId === bank.id
                              ? "border-primary bg-primary/5"
                              : "border-border bg-background hover:border-primary/50"
                          }`}
                        >
                          <div className="min-w-0">
                            <p className="text-sm font-semibold text-foreground">{bank.shortName}</p>
                            <p className="text-xs text-muted-foreground">
                              {bank.fullName}
                              {bank.bin ? " · BIN " + bank.bin : ""}
                            </p>
                            <p className="mt-0.5 text-xs text-muted-foreground">
                              {getPaymentMethodLabel(bank.provider)}
                            </p>
                          </div>
                          <button
                            type="button"
                            disabled={isPending}
                            onClick={() => handleAuthorize(bank)}
                            className={`shrink-0 rounded-full px-4 py-1.5 text-xs font-semibold transition-opacity ${
                              selectedBankId === bank.id && isPending
                                ? "bg-muted text-muted-foreground cursor-not-allowed"
                                : "bg-primary text-primary-foreground hover:opacity-90"
                            }`}
                          >
                            {selectedBankId === bank.id && isPending ? "Dang xu ly..." : "Thanh toan"}
                          </button>
                        </div>
                      ))}
                  </div>
                )}
              </section>
            )}

            {/* Authorized / captured notice */}
            {(isAuthorized || isCaptured) && isPayable ? (
              <section className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
                <p className="font-semibold">
                  {isCaptured
                    ? "Thanh toan da hoan tat."
                    : "Tien da duoc giu thanh cong."}
                </p>
                {booking.status === "PENDING_HOST_APPROVAL" ? (
                  <p className="mt-1 text-xs">Dang cho chu xe xac nhan.</p>
                ) : booking.status === "CONFIRMED" ? (
                  <p className="mt-1 text-xs">Booking da duoc xac nhan.</p>
                ) : null}
              </section>
            ) : null}

            {/* Non-payable notice */}
            {!isPayable && !hasPayment ? (
              <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
                <p className="text-sm text-muted-foreground">
                  Booking dang o trang thai <strong>{booking.status}</strong>, khong the thanh toan tu giao dien nay.
                </p>
              </section>
            ) : null}

            {/* Technical details (collapsible) */}
            {hasPayment && (payment!.payment.externalOrderRef || payment!.payment.providerPaymentOrderId || payment!.payment.providerHoldId) ? (
              <section className="rounded-xl border border-border bg-card shadow-sm">
                <button
                  type="button"
                  onClick={() => setTechDetailsOpen((o) => !o)}
                  className="flex w-full items-center justify-between p-4 text-sm font-semibold text-foreground hover:bg-accent"
                >
                  Chi tiet ky thuat
                  {techDetailsOpen ? (
                    <ChevronUp className="h-4 w-4" />
                  ) : (
                    <ChevronDown className="h-4 w-4" />
                  )}
                </button>
                {techDetailsOpen ? (
                  <div className="border-t border-border p-4">
                    <dl className="grid gap-2 text-xs">
                      {payment!.payment.externalOrderRef ? (
                        <>
                          <dt className="text-muted-foreground">externalOrderRef</dt>
                          <dd className="font-mono text-foreground">{payment!.payment.externalOrderRef}</dd>
                        </>
                      ) : null}
                      {payment!.payment.providerPaymentOrderId ? (
                        <>
                          <dt className="mt-2 text-muted-foreground">providerPaymentOrderId</dt>
                          <dd className="font-mono text-foreground">{payment!.payment.providerPaymentOrderId}</dd>
                        </>
                      ) : null}
                      {payment!.payment.providerHoldId ? (
                        <>
                          <dt className="mt-2 text-muted-foreground">providerHoldId</dt>
                          <dd className="font-mono text-foreground">{payment!.payment.providerHoldId}</dd>
                        </>
                      ) : null}
                      {payment!.transactions && payment!.transactions.length > 0 ? (
                        <>
                          <dt className="mt-3 font-semibold text-foreground">Giao dich</dt>
                          {payment!.transactions.map((tx) => (
                            <dd key={tx.id} className="mt-1 rounded border border-border bg-background p-2">
                              <div className="font-medium">{tx.type} — {tx.status}</div>
                              <div className="mt-0.5 text-muted-foreground">
                                {formatMoney(tx.amount, tx.currency)} · {tx.provider}
                              </div>
                              {tx.providerRef ? (
                                <div className="mt-0.5 font-mono text-muted-foreground">
                                  ref: {tx.providerRef}
                                </div>
                              ) : null}
                              {tx.providerErrorMessage ? (
                                <div className="mt-0.5 text-rose-700">{tx.providerErrorMessage}</div>
                              ) : null}
                            </dd>
                          ))}
                        </>
                      ) : null}
                    </dl>
                  </div>
                ) : null}
              </section>
            ) : null}
          </div>

          {/* Right column: sticky payment summary */}
          <div className="w-full lg:sticky lg:top-6 lg:w-80 lg:shrink-0">
            <div className="rounded-xl border border-border bg-card p-5 shadow-sm space-y-4">
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  {booking.listingTitle}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {booking.pickupDate} → {booking.returnDate}
                </p>
              </div>

              <div className="border-t border-border pt-4 space-y-2">
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Tong tien</span>
                  <span className="font-semibold text-foreground">
                    {formatMoney(booking.totalAmount, booking.currency)}
                  </span>
                </div>
                {hasPayment ? (
                  <>
                    {payment!.payment.authorizedAmount > 0 ? (
                      <div className="flex justify-between text-sm">
                        <span className="text-muted-foreground">Da giu</span>
                        <span className="font-medium text-foreground">
                          {formatMoney(payment!.payment.authorizedAmount, payment!.payment.currency)}
                        </span>
                      </div>
                    ) : null}
                    {payment!.payment.capturedAmount > 0 ? (
                      <div className="flex justify-between text-sm">
                        <span className="text-muted-foreground">Da thanh toan</span>
                        <span className="font-medium text-emerald-700">
                          {formatMoney(payment!.payment.capturedAmount, payment!.payment.currency)}
                        </span>
                      </div>
                    ) : null}
                    {payment!.payment.refundedAmount > 0 ? (
                      <div className="flex justify-between text-sm">
                        <span className="text-muted-foreground">Da hoan</span>
                        <span className="font-medium text-foreground">
                          {formatMoney(payment!.payment.refundedAmount, payment!.payment.currency)}
                        </span>
                      </div>
                    ) : null}
                  </>
                ) : null}
              </div>

              {hasPayment && payment!.payment.status === "AUTHORIZED" && (
                <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-center text-sm text-emerald-900">
                  <p className="font-semibold">Tien da duoc giu</p>
                  <p className="mt-0.5 text-xs">Dang cho xac nhan tu chu xe</p>
                </div>
              )}

              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <ShieldCheck className="h-3.5 w-3.5 shrink-0" />
                Thanh toan duoc ma hoa va bao mat
              </div>
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  function handleCopy() {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }
  return (
    <button
      type="button"
      onClick={handleCopy}
      className="text-muted-foreground hover:text-foreground"
      title="Copy"
    >
      <Copy className={`h-3.5 w-3.5 ${copied ? "text-emerald-600" : ""}`} />
    </button>
  );
}
