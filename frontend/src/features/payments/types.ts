export type PaymentMethod = "BANK_TRANSFER_QR" | "COREBANK_TRANSFER";

export type PaymentProviderType = "VIETQR_MANUAL" | "COREBANK" | "STUB" | string;

export type PaymentStatus =
  | "UNPAID"
  | "AUTHORIZED"
  | "CAPTURED"
  | "PARTIALLY_REFUNDED"
  | "REFUNDED"
  | "VOIDED"
  | "FAILED";

export type PaymentBank = {
  id: string;
  code: string;
  bin: string | null;
  shortName: string;
  fullName: string;
  paymentMethod: PaymentMethod;
  provider: PaymentProviderType;
  active: boolean;
};

export type TransferInstruction = {
  bankCode: string;
  bankBin: string;
  accountNumber: string;
  accountName: string;
  amount: number;
  content: string;
  qrPayload: string;
};

export type PaymentBookingSummary = {
  id: string;
  status: string;
  pickupDate: string;
  returnDate: string;
  totalAmount: number;
  currency: string;
};

export type PaymentPaymentSummary = {
  id: string;
  status: PaymentStatus;
  paymentMethod: PaymentMethod | null;
  provider: PaymentProviderType | null;
  externalOrderRef: string | null;
  providerPaymentOrderId: string | null;
  providerHoldId: string | null;
  authorizedAmount: number;
  capturedAmount: number;
  refundedAmount: number;
  currency: string;
  transferInstruction: TransferInstruction | null;
};

export type AuthorizePaymentResult = {
  booking: PaymentBookingSummary;
  payment: PaymentPaymentSummary;
};

export type PaymentTransactionSummary = {
  id: string;
  type: string;
  status: string;
  amount: number;
  currency: string;
  provider: PaymentProviderType;
  providerRequestId: string | null;
  providerRef: string | null;
  providerJournalId: string | null;
  providerErrorCode: string | null;
  providerErrorMessage: string | null;
  createdAt: string;
};

export type PaymentDetail = {
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
    paymentMethod: PaymentMethod | null;
    provider: PaymentProviderType | null;
    status: PaymentStatus;
    authorizedAmount: number;
    capturedAmount: number;
    refundedAmount: number;
    currency: string;
    externalOrderRef: string | null;
    providerPaymentOrderId: string | null;
    providerHoldId: string | null;
    providerStatus: string | null;
    transferInstruction: TransferInstruction | null;
  };
  transactions: PaymentTransactionSummary[];
};