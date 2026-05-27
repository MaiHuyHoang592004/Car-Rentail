# Payment Provider Architecture — Bank Transfer + CoreBank

## Goal

Design RentFlow payments so a customer can choose a Vietnamese bank for bank-transfer-style payment while keeping the backend provider-agnostic.

CoreBank API is included as the first real provider for portfolio demo purposes. It proves financial correctness through hold/capture/void/refund, idempotency, ledger journals, and reconciliation.

## Positioning

RentFlow is not the financial ledger.

RentFlow owns:
- booking lifecycle
- local payment aggregate and payment transactions
- provider routing
- idempotency mapping
- payment UI state
- reconciliation state

Payment providers own:
- bank account balances
- transfer/hold/capture/refund execution
- provider-side payment order state
- provider-side ledger/journal correctness

CoreBank owns, for the demo:
- account available balance and posted balance
- payment orders
- holds
- capture journals
- refund reversal journals
- payment query source of truth

## Supported payment methods

### 1. `BANK_TRANSFER_QR`

Customer chooses a Vietnamese bank from the bank catalog. RentFlow generates a bank-transfer instruction or VietQR-style payment payload.

This method is appropriate for real Vietnamese bank transfer UX where the app shows:
- selected bank
- beneficiary account
- amount
- transfer content
- QR code or transfer instruction

Provider execution may be manual or automated depending on available banking integration.

### 2. `COREBANK_TRANSFER`

Customer chooses `CoreBank Demo Bank` from the same bank selector.

RentFlow calls CoreBank API directly:
- authorize hold
- capture hold
- void hold
- refund
- query order by `externalOrderRef`

This method is the portfolio-grade demo path.

### 3. Future providers

Future providers may include:
- NAPAS/VietQR aggregator
- individual bank APIs
- domestic wallet providers
- card gateway

They must implement the same provider contract.

## Bank catalog design

Do not hardcode a handful of banks in frontend code.

Use a backend-owned bank catalog:

```text
payment_banks
├── id
├── code                 # VCB, BIDV, TCB, MB, COREBANK, ...
├── bin                  # bank BIN/NAPAS/VietQR code when available
├── short_name           # Vietcombank, BIDV, Techcombank, CoreBank Demo
├── full_name
├── logo_url
├── country_code         # VN
├── payment_method       # BANK_TRANSFER_QR, COREBANK_TRANSFER
├── provider             # VIETQR_MANUAL, COREBANK, BANK_API, DISABLED
├── active
├── display_order
├── metadata             # JSONB provider-specific data
├── created_at
└── updated_at
```

Rules:
- Frontend fetches active banks from `GET /api/v1/payment-banks`.
- The bank list must be seedable and refreshable.
- The backend decides which provider handles a bank.
- `COREBANK` appears as one selectable bank for demo.
- Real Vietnamese banks can initially route to `VIETQR_MANUAL` while CoreBank routes to `COREBANK`.

Recommended MVP catalog groups:
- Major state/commercial banks: Vietcombank, BIDV, VietinBank, Agribank.
- Major private banks: Techcombank, MB, VPBank, ACB, TPBank, VIB, HDBank, SHB, MSB, OCB, Sacombank, Eximbank.
- Foreign/wholly foreign-owned banks in Vietnam can be added if transfer instructions are supported.
- `CoreBank Demo Bank` must be present for the end-to-end provider demo.

Important: "all Vietnamese banks" should be implemented as a maintainable catalog, not as frontend constants. The catalog can be seeded from a curated official/provider source and updated without redeploying frontend code.

## Provider abstraction

```java
public interface PaymentProvider {
    AuthorizeResult authorize(AuthorizeCommand command);
    CaptureResult capture(CaptureCommand command);
    VoidResult voidAuthorization(VoidCommand command);
    RefundResult refund(RefundCommand command);
    PaymentProviderOrder getOrder(String providerPaymentOrderId);
    PaymentProviderOrder findByExternalOrderRef(String externalOrderRef);
}
```

Implementations:

```text
PaymentProvider
├── StubPaymentProvider          # tests/local dev
├── BankTransferQrProvider       # creates transfer instruction, no real money execution
└── CoreBankPaymentProvider      # real demo provider backed by corebank-api
```

Routing:

```text
selectedBank.provider = COREBANK      -> CoreBankPaymentProvider
selectedBank.provider = VIETQR_MANUAL -> BankTransferQrProvider
selectedBank.provider = STUB          -> StubPaymentProvider
```

Do not branch business logic directly by bank name inside `PaymentService`.

## CoreBank API contract

CoreBank provider endpoints:

```http
POST /api/payments/authorize-hold
POST /api/payments/capture-hold
POST /api/payments/void-hold
POST /api/payments/refund
GET  /api/payments/orders/{paymentOrderId}
GET  /api/payments/orders?externalOrderRef=...
```

CoreBank authorize request includes:

```json
{
  "idempotencyKey": "rentflow:authorize:{bookingId}:{key}",
  "payerAccountId": "customer-corebank-account-id",
  "payeeAccountId": "rentflow-escrow-corebank-account-id",
  "amountMinor": 1400000,
  "currency": "VND",
  "paymentType": "MERCHANT_PAYMENT",
  "description": "RentFlow booking payment",
  "externalOrderRef": "rentflow:booking:{bookingId}",
  "actor": "rentflow",
  "correlationId": "uuid",
  "requestId": "uuid",
  "sessionId": "uuid",
  "traceId": "trace-id"
}
```

`externalOrderRef` is the main cross-system reference.

Recommended format:

```text
rentflow:booking:{bookingId}
```

It must remain within the CoreBank allowed character set and length:
- letters
- digits
- colon
- underscore
- hyphen
- max 128 characters

## RentFlow local state

RentFlow should keep a local payment aggregate even when CoreBank is source of truth for money.

`booking_payments` should track:
- booking id
- selected bank id
- payment method
- provider
- status
- authorized/captured/refunded amounts
- currency
- external order ref
- provider payment order id
- provider hold id
- provider status
- provider metadata

`payment_transactions` should track each provider call:
- local payment id
- type: AUTHORIZE, CAPTURE, VOID, REFUND
- status: PENDING, SUCCEEDED, FAILED, COMPENSATION_REQUIRED
- amount
- provider
- provider request id
- provider reference
- provider journal id
- provider response/error JSON
- idempotency key id

## Recommended payment statuses

Local `booking_payments.status`:

```text
UNPAID
PENDING_TRANSFER
AUTHORIZED
CAPTURED
PARTIALLY_REFUNDED
REFUNDED
VOIDED
FAILED
RECONCILIATION_REQUIRED
```

Provider status should be stored separately because provider state may use a different state machine.

## Bank-transfer flow

### A. Generic Vietnamese bank transfer / QR path

1. Customer chooses a bank from `GET /api/v1/payment-banks`.
2. RentFlow creates a payment instruction.
3. Booking remains `HELD` or moves to `PENDING_PAYMENT`, depending on booking design.
4. Customer completes transfer outside RentFlow.
5. RentFlow marks payment as paid only after confirmation.
6. Confirmation can come from manual admin action, polling provider API, or webhook/reconciliation in later phases.

MVP rule:
- Do not mark a generic bank transfer as `AUTHORIZED` only because a QR was generated.
- QR generation is not money movement.

### B. CoreBank demo path

1. Customer chooses `CoreBank Demo Bank`.
2. RentFlow maps the user to a CoreBank payer account.
3. RentFlow calls `authorize-hold`.
4. CoreBank decreases available balance but does not change posted balance.
5. RentFlow stores `paymentOrderId`, `holdId`, and `externalOrderRef`.
6. RentFlow finalizes booking state:
   - `instantBook=true`: `HELD -> CONFIRMED`, availability `HOLD -> BOOKED`
   - `instantBook=false`: `HELD -> PENDING_HOST_APPROVAL`

### C. Capture

1. RentFlow calls CoreBank `capture-hold`.
2. CoreBank creates a double-entry journal.
3. RentFlow stores `journalId` on the local transaction.
4. RentFlow updates local captured amount/status.

### D. Void

1. RentFlow calls CoreBank `void-hold` before capture.
2. CoreBank restores available balance.
3. RentFlow marks local payment as `VOIDED`.

### E. Refund

1. RentFlow calls CoreBank `refund` by `paymentOrderId`.
2. CoreBank creates refund/reversal journal.
3. RentFlow stores `refundJournalId`.
4. RentFlow updates local refunded amount/status.

## Transaction and reliability model

Do not hold RentFlow booking/availability locks while making slow external HTTP calls when avoidable.

Recommended pattern for external providers:

```text
TX1 RentFlow:
- lock idempotency key
- lock booking/payment
- validate business preconditions
- create local payment transaction PENDING
- commit

External call:
- call provider with provider idempotency key

TX2 RentFlow:
- lock booking/payment again
- verify state is still compatible
- store provider refs/response
- transition booking/payment/availability
- commit
```

If provider succeeds but RentFlow finalization fails:
- mark local transaction `COMPENSATION_REQUIRED`
- attempt provider void/refund depending on operation
- reconciliation job must detect and repair drift

For CoreBank, provider idempotency key must be deterministic and scoped by operation:

```text
rentflow:{operation}:{bookingId}:{clientIdempotencyKey}
```

Examples:

```text
rentflow:authorize:booking-uuid:client-key
rentflow:capture:payment-uuid:client-key
rentflow:void:payment-uuid:client-key
rentflow:refund:payment-uuid:client-key
```

## Reconciliation

CoreBank supports query by `externalOrderRef`, so RentFlow can reconcile:

```http
GET /api/payments/orders?externalOrderRef=rentflow:booking:{bookingId}
```

Reconciliation should compare:
- local provider payment order id
- local provider hold id
- local amount/currency
- local captured/refunded amounts
- provider status
- provider events/journals

If mismatched:
- mark `RECONCILIATION_REQUIRED`
- create audit entry
- expose admin/ops endpoint in a later phase

## API contracts for RentFlow

### List payment banks

```http
GET /api/v1/payment-banks
```

Response:

```json
{
  "items": [
    {
      "id": "uuid",
      "code": "VCB",
      "shortName": "Vietcombank",
      "fullName": "Joint Stock Commercial Bank for Foreign Trade of Vietnam",
      "bin": "970436",
      "paymentMethod": "BANK_TRANSFER_QR",
      "provider": "VIETQR_MANUAL",
      "active": true
    },
    {
      "id": "uuid",
      "code": "COREBANK",
      "shortName": "CoreBank Demo Bank",
      "fullName": "CoreBank Demo Bank",
      "bin": null,
      "paymentMethod": "COREBANK_TRANSFER",
      "provider": "COREBANK",
      "active": true
    }
  ]
}
```

### Authorize booking payment

```http
POST /api/v1/bookings/{bookingId}/payments/authorize
Idempotency-Key: uuid
```

Request:

```json
{
  "bankId": "uuid",
  "paymentMethod": "COREBANK_TRANSFER"
}
```

Response should include:

```json
{
  "booking": {
    "id": "uuid",
    "status": "CONFIRMED"
  },
  "payment": {
    "id": "uuid",
    "status": "AUTHORIZED",
    "paymentMethod": "COREBANK_TRANSFER",
    "provider": "COREBANK",
    "externalOrderRef": "rentflow:booking:uuid",
    "providerPaymentOrderId": "uuid",
    "providerHoldId": "uuid",
    "authorizedAmount": 1400000,
    "capturedAmount": 0,
    "refundedAmount": 0,
    "currency": "VND"
  }
}
```

For `BANK_TRANSFER_QR`, response should include transfer instructions instead of provider hold ids.

## Demo narrative

Demo path:

1. In RentFlow, choose `CoreBank Demo Bank` at payment step.
2. RentFlow calls CoreBank authorize hold.
3. Show RentFlow booking becomes confirmed.
4. Open CoreBank dashboard/query endpoint.
5. Show available balance decreased while posted balance remains unchanged after authorize.
6. Capture payment.
7. Show CoreBank journal created and balanced.
8. Refund payment.
9. Show reversal/refund journal and RentFlow reconciliation by `externalOrderRef`.

Interview message:

> RentFlow orchestrates booking and payment state. CoreBank owns financial truth. The integration is provider-agnostic, idempotent, reconcilable, and demonstrates module boundaries across two independently developed systems.
