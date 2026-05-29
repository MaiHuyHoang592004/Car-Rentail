import { api } from "@/lib/api-client";
import type {
  AuthorizePaymentResult,
  PaymentBank,
  PaymentDetail,
  PaymentMethod,
} from "@/features/payments/types";

/* ------------------------------------------------------------------ */
/*  Raw backend response types (kept internal)                        */
/* ------------------------------------------------------------------ */

type RawBankResponse = {
  id: string;
  code: string;
  bin: string | null;
  shortName: string;
  fullName: string;
  paymentMethod: string;
  provider: string;
  active: boolean;
};

type RawTransferInstruction = {
  bankCode: string;
  bankBin: string;
  accountNumber: string;
  accountName: string;
  amount: number | string;
  content: string;
  qrPayload: string;
};

type RawAuthorizePaymentResponse = {
  booking: {
    id: string;
    status: string;
    pickupDate: string;
    returnDate: string;
    totalAmount: number | string;
    currency: string;
  };
  payment: {
    id: string;
    status: string;
    paymentMethod: string | null;
    provider: string | null;
    externalOrderRef: string | null;
    providerPaymentOrderId: string | null;
    providerHoldId: string | null;
    authorizedAmount: number | string;
    capturedAmount: number | string;
    refundedAmount: number | string;
    currency: string;
    transferInstruction: RawTransferInstruction | null;
  };
};

type RawPaymentDetailResponse = {
  booking: {
    id: string;
    customerId: string;
    hostId: string;
    status: string;
    pickupDate: string;
    returnDate: string;
  };
  payment: {
    id: string;
    selectedBankId: string | null;
    paymentMethod: string | null;
    provider: string | null;
    status: string;
    authorizedAmount: number | string;
    capturedAmount: number | string;
    refundedAmount: number | string;
    currency: string;
    externalOrderRef: string | null;
    providerPaymentOrderId: string | null;
    providerHoldId: string | null;
    providerStatus: string | null;
    transferInstruction: RawTransferInstruction | null;
  };
  transactions: Array<{
    id: string;
    type: string;
    status: string;
    amount: number | string;
    currency: string;
    provider: string;
    providerRequestId: string | null;
    providerRef: string | null;
    providerJournalId: string | null;
    providerErrorCode: string | null;
    providerErrorMessage: string | null;
    createdAt: string;
  }>;
};

/* ------------------------------------------------------------------ */
/*  Helpers                                                           */
/* ------------------------------------------------------------------ */

function toNumber(value: number | string | null | undefined): number {
  if (value === null || value === undefined) return 0;
  if (typeof value === "number") return value;
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function mapTransferInstruction(
  raw: RawTransferInstruction | null,
): {
  bankCode: string;
  bankBin: string;
  accountNumber: string;
  accountName: string;
  amount: number;
  content: string;
  qrPayload: string;
} | null {
  if (!raw) return null;
  return {
    bankCode: raw.bankCode,
    bankBin: raw.bankBin,
    accountNumber: raw.accountNumber,
    accountName: raw.accountName,
    amount: toNumber(raw.amount),
    content: raw.content,
    qrPayload: raw.qrPayload,
  };
}

/* ------------------------------------------------------------------ */
/*  Payment banks                                                     */
/* ------------------------------------------------------------------ */

function mapBank(raw: RawBankResponse): PaymentBank {
  return {
    id: raw.id,
    code: raw.code,
    bin: raw.bin,
    shortName: raw.shortName,
    fullName: raw.fullName,
    paymentMethod: raw.paymentMethod as PaymentMethod,
    provider: raw.provider,
    active: raw.active,
  };
}

export async function listPaymentBanks(signal?: AbortSignal): Promise<PaymentBank[]> {
  const raw = await api.get<{ items: RawBankResponse[] }>("/payment-banks", { signal });
  return (raw.items ?? []).map(mapBank);
}

/* ------------------------------------------------------------------ */
/*  Authorize booking payment                                         */
/* ------------------------------------------------------------------ */

export type AuthorizePaymentInput = {
  bankId: string;
  paymentMethod: PaymentMethod;
};

export async function authorizeBookingPayment(
  bookingId: string,
  input: AuthorizePaymentInput,
  idempotencyKey: string,
): Promise<AuthorizePaymentResult> {
  const raw = await api.post<RawAuthorizePaymentResponse>(
    `/bookings/${bookingId}/payments/authorize`,
    { bankId: input.bankId, paymentMethod: input.paymentMethod },
    { idempotencyKey },
  );

  return {
    booking: {
      id: raw.booking.id,
      status: raw.booking.status,
      pickupDate: raw.booking.pickupDate,
      returnDate: raw.booking.returnDate,
      totalAmount: toNumber(raw.booking.totalAmount),
      currency: raw.booking.currency,
    },
    payment: {
      id: raw.payment.id,
      status: raw.payment.status as PaymentDetail["payment"]["status"],
      paymentMethod: raw.payment.paymentMethod as PaymentMethod | null,
      provider: raw.payment.provider,
      externalOrderRef: raw.payment.externalOrderRef,
      providerPaymentOrderId: raw.payment.providerPaymentOrderId,
      providerHoldId: raw.payment.providerHoldId,
      authorizedAmount: toNumber(raw.payment.authorizedAmount),
      capturedAmount: toNumber(raw.payment.capturedAmount),
      refundedAmount: toNumber(raw.payment.refundedAmount),
      currency: raw.payment.currency,
      transferInstruction: mapTransferInstruction(raw.payment.transferInstruction),
    },
  };
}

/* ------------------------------------------------------------------ */
/*  Get existing payment for a booking                                */
/* ------------------------------------------------------------------ */

export async function getBookingPayment(
  bookingId: string,
  signal?: AbortSignal,
): Promise<PaymentDetail | null> {
  try {
    const raw = await api.get<RawPaymentDetailResponse>(
      `/bookings/${bookingId}/payments`,
      { signal },
    );
    return {
      booking: {
        id: raw.booking.id,
        customerId: raw.booking.customerId,
        hostId: raw.booking.hostId,
        status: raw.booking.status,
        pickupDate: raw.booking.pickupDate,
        returnDate: raw.booking.returnDate,
      },
      payment: {
        id: raw.payment.id,
        selectedBankId: raw.payment.selectedBankId,
        paymentMethod: raw.payment.paymentMethod as PaymentMethod | null,
        provider: raw.payment.provider,
        status: raw.payment.status as PaymentDetail["payment"]["status"],
        authorizedAmount: toNumber(raw.payment.authorizedAmount),
        capturedAmount: toNumber(raw.payment.capturedAmount),
        refundedAmount: toNumber(raw.payment.refundedAmount),
        currency: raw.payment.currency,
        externalOrderRef: raw.payment.externalOrderRef,
        providerPaymentOrderId: raw.payment.providerPaymentOrderId,
        providerHoldId: raw.payment.providerHoldId,
        providerStatus: raw.payment.providerStatus,
        transferInstruction: mapTransferInstruction(raw.payment.transferInstruction),
      },
      transactions: raw.transactions.map((t) => ({
        id: t.id,
        type: t.type,
        status: t.status,
        amount: toNumber(t.amount),
        currency: t.currency,
        provider: t.provider,
        providerRequestId: t.providerRequestId,
        providerRef: t.providerRef,
        providerJournalId: t.providerJournalId,
        providerErrorCode: t.providerErrorCode,
        providerErrorMessage: t.providerErrorMessage,
        createdAt: t.createdAt,
      })),
    };
  } catch (err) {
    // If no payment exists yet, return null instead of throwing
    if (err instanceof Error && "status" in err) {
      const status = (err as unknown as { status?: number }).status;
      if (status === 404) return null;
    }
    throw err;
  }
}