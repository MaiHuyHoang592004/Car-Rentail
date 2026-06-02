import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  authorizeBookingPayment,
  getBookingPayment,
  listPaymentBanks,
  simulateTransferConfirmation,
} from "./api";

describe("payment api", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  }

  /* ---------------------------------------------------------------- */
  /*  listPaymentBanks                                                */
  /* ---------------------------------------------------------------- */

  describe("listPaymentBanks", () => {
    it("fetches GET /payment-banks and maps items", async () => {
      fetchSpy.mockResolvedValueOnce(
        jsonResponse({
          items: [
            {
              id: "bank-1",
              code: "VCB",
              bin: "970436",
              shortName: "Vietcombank",
              fullName: "Vietnam Joint Stock Commercial Bank",
              paymentMethod: "BANK_TRANSFER_QR",
              provider: "VIETQR_MANUAL",
              active: true,
            },
            {
              id: "bank-2",
              code: "COREBANK",
              bin: null,
              shortName: "CoreBank Demo",
              fullName: "CoreBank Demo Bank",
              paymentMethod: "COREBANK_TRANSFER",
              provider: "COREBANK",
              active: true,
            },
          ],
        }),
      );

      const banks = await listPaymentBanks();
      expect(banks).toHaveLength(2);
      expect(banks[0].id).toBe("bank-1");
      expect(banks[0].shortName).toBe("Vietcombank");
      expect(banks[0].paymentMethod).toBe("BANK_TRANSFER_QR");
      expect(banks[1].provider).toBe("COREBANK");

      const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
      expect(url).toBe("/api/v1/payment-banks");
      expect(init.method).toBe("GET");
    });

    it("returns empty array for empty items", async () => {
      fetchSpy.mockResolvedValueOnce(jsonResponse({ items: [] }));
      const banks = await listPaymentBanks();
      expect(banks).toEqual([]);
    });
  });

  /* ---------------------------------------------------------------- */
  /*  authorizeBookingPayment                                         */
  /* ---------------------------------------------------------------- */

  describe("authorizeBookingPayment", () => {
    it("sends Idempotency-Key header and maps response", async () => {
      fetchSpy.mockResolvedValueOnce(
        jsonResponse({
          booking: {
            id: "bk-1",
            status: "CONFIRMED",
            pickupDate: "2026-06-01",
            returnDate: "2026-06-03",
            totalAmount: 1400000,
            currency: "VND",
          },
          payment: {
            id: "pay-1",
            status: "AUTHORIZED",
            paymentMethod: "COREBANK_TRANSFER",
            provider: "COREBANK",
            externalOrderRef: "rentflow:booking:bk-1",
            providerPaymentOrderId: "cb-order-1",
            providerHoldId: "cb-hold-1",
            authorizedAmount: 1400000,
            capturedAmount: 0,
            refundedAmount: 0,
            currency: "VND",
            transferInstruction: null,
          },
        }),
      );

      const result = await authorizeBookingPayment(
        "bk-1",
        { bankId: "bank-2", paymentMethod: "COREBANK_TRANSFER" },
        "11111111-1111-4111-8111-111111111111",
      );

      expect(result.booking.id).toBe("bk-1");
      expect(result.booking.status).toBe("CONFIRMED");
      expect(result.payment.status).toBe("AUTHORIZED");
      expect(result.payment.authorizedAmount).toBe(1400000);
      expect(result.payment.transferInstruction).toBeNull();

      const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
      expect(url).toBe("/api/v1/bookings/bk-1/payments/authorize");
      expect(init.method).toBe("POST");
      const headers = new Headers(init.headers);
      expect(headers.get("Idempotency-Key")).toBe("11111111-1111-4111-8111-111111111111");
      const body = JSON.parse(init.body as string);
      expect(body).toEqual({
        bankId: "bank-2",
        paymentMethod: "COREBANK_TRANSFER",
      });
    });

    it("maps transfer instruction when present", async () => {
      fetchSpy.mockResolvedValueOnce(
        jsonResponse({
          booking: {
            id: "bk-2",
            status: "HELD",
            pickupDate: "2026-06-10",
            returnDate: "2026-06-12",
            totalAmount: 2000000,
            currency: "VND",
          },
          payment: {
            id: "pay-2",
            status: "UNPAID",
            paymentMethod: "BANK_TRANSFER_QR",
            provider: "VIETQR_MANUAL",
            externalOrderRef: null,
            providerPaymentOrderId: null,
            providerHoldId: null,
            authorizedAmount: 0,
            capturedAmount: 0,
            refundedAmount: 0,
            currency: "VND",
            transferInstruction: {
              bankCode: "VCB",
              bankBin: "970436",
              accountNumber: "1234567890",
              accountName: "RentFlow",
              amount: 2000000,
              content: "RentFlow BK bk-2",
              qrPayload: "qr://payload",
            },
          },
        }),
      );

      const result = await authorizeBookingPayment(
        "bk-2",
        { bankId: "bank-1", paymentMethod: "BANK_TRANSFER_QR" },
        "22222222-2222-4222-8222-222222222222",
      );

      expect(result.payment.status).toBe("UNPAID");
      expect(result.payment.transferInstruction).not.toBeNull();
      expect(result.payment.transferInstruction!.bankCode).toBe("VCB");
      expect(result.payment.transferInstruction!.accountNumber).toBe("1234567890");
      expect(result.payment.transferInstruction!.amount).toBe(2000000);
      expect(result.payment.transferInstruction!.qrPayload).toBe("qr://payload");
    });
  });

  /* ---------------------------------------------------------------- */
  /*  getBookingPayment                                               */
  /* ---------------------------------------------------------------- */

  describe("getBookingPayment", () => {
    it("fetches payment detail and normalizes numbers", async () => {
      fetchSpy.mockResolvedValueOnce(
        jsonResponse({
          booking: {
            id: "bk-1",
            customerId: "u-1",
            hostId: "h-1",
            status: "CONFIRMED",
            pickupDate: "2026-06-01",
            returnDate: "2026-06-03",
          },
          payment: {
            id: "pay-1",
            selectedBankId: "bank-1",
            paymentMethod: "BANK_TRANSFER_QR",
            provider: "VIETQR_MANUAL",
            status: "AUTHORIZED",
            authorizedAmount: "1400000",
            capturedAmount: "0",
            refundedAmount: "0",
            currency: "VND",
            externalOrderRef: null,
            providerPaymentOrderId: null,
            providerHoldId: null,
            providerStatus: null,
            transferInstruction: null,
          },
          transactions: [
            {
              id: "txn-1",
              type: "AUTHORIZE",
              status: "SUCCEEDED",
              amount: "1400000",
              currency: "VND",
              provider: "VIETQR_MANUAL",
              providerRequestId: null,
              providerRef: null,
              providerJournalId: null,
              providerErrorCode: null,
              providerErrorMessage: null,
              createdAt: "2026-06-01T00:00:00Z",
            },
          ],
        }),
      );

      const detail = await getBookingPayment("bk-1");
      expect(detail).not.toBeNull();
      expect(detail!.payment.status).toBe("AUTHORIZED");
      expect(detail!.payment.authorizedAmount).toBe(1400000);
      expect(detail!.transactions).toHaveLength(1);
      expect(detail!.transactions[0].amount).toBe(1400000);
      expect(detail!.transactions[0].type).toBe("AUTHORIZE");
    });

    it("returns null on 404 (no payment yet)", async () => {
      fetchSpy.mockResolvedValueOnce(
        new Response(JSON.stringify({ code: "NOT_FOUND", message: "No payment" }), {
          status: 404,
          headers: { "Content-Type": "application/json" },
        }),
      );

      const detail = await getBookingPayment("bk-new");
      expect(detail).toBeNull();
    });

    it("re-throws non-404 errors", async () => {
      fetchSpy.mockResolvedValueOnce(
        new Response(JSON.stringify({ code: "ACCESS_DENIED", message: "Forbidden" }), {
          status: 403,
          headers: { "Content-Type": "application/json" },
        }),
      );

      await expect(getBookingPayment("bk-err")).rejects.toThrow();
    });
  });

  describe("simulateTransferConfirmation", () => {
    it("posts to sandbox confirmation endpoint with Idempotency-Key and maps payment detail", async () => {
      fetchSpy.mockResolvedValueOnce(
        jsonResponse({
          booking: {
            id: "bk-1",
            customerId: "u-1",
            hostId: "h-1",
            status: "PENDING_HOST_APPROVAL",
            pickupDate: "2026-06-01",
            returnDate: "2026-06-03",
          },
          payment: {
            id: "pay-1",
            selectedBankId: "bank-1",
            paymentMethod: "BANK_TRANSFER_QR",
            provider: "VIETQR_MANUAL",
            status: "AUTHORIZED",
            authorizedAmount: "1400000",
            capturedAmount: "0",
            refundedAmount: "0",
            currency: "VND",
            externalOrderRef: "rentflow:booking:bk-1",
            providerPaymentOrderId: null,
            providerHoldId: null,
            providerStatus: "SANDBOX_TRANSFER_CONFIRMED",
            transferInstruction: null,
          },
          transactions: [],
        }),
      );

      const detail = await simulateTransferConfirmation(
        "bk-1",
        "33333333-3333-4333-8333-333333333333",
      );

      expect(detail.payment.status).toBe("AUTHORIZED");
      expect(detail.payment.providerStatus).toBe("SANDBOX_TRANSFER_CONFIRMED");
      const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
      expect(url).toBe("/api/v1/bookings/bk-1/payments/simulate-transfer-confirmation");
      expect(init.method).toBe("POST");
      expect(new Headers(init.headers).get("Idempotency-Key")).toBe(
        "33333333-3333-4333-8333-333333333333",
      );
    });
  });
});
