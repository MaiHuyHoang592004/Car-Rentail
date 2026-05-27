# Phase 06 — Payment Provider + Bank Transfer

## Goal

Implement RentFlow payment as a provider-agnostic bank-transfer orchestration module.

Customer can choose a Vietnamese bank at checkout. The selected bank routes to one of these provider paths:

- `COREBANK_TRANSFER`: calls the CoreBank API for portfolio demo and real financial correctness proof.
- `BANK_TRANSFER_QR`: generates Vietnamese bank transfer / VietQR-style payment instructions for real bank selection UX.
- `STUB`: local/test provider only.

CoreBank is the first real provider because it supports authorize hold, capture, void, refund, external order reference, and query/reconciliation endpoints.

Read first:

- `docs/payment-provider-architecture.md`
- `docs/context/payment-rules.md`
- `docs/transaction-rules.md`
- CoreBank payment API contract in `corebank-api`

## Must Implement

### Payment aggregate

- [ ] `BookingPayment` entity + repository
- [ ] `PaymentTransaction` entity + repository
- [ ] `PaymentBank` entity + repository for selectable banks
- [ ] Flyway migration for payment tables and bank catalog seed
- [ ] Local payment status machine
- [ ] Provider references stored for every provider call
- [ ] Provider response/error metadata stored for audit/reconciliation

### Provider abstraction

- [ ] `PaymentProvider` interface
- [ ] `StubPaymentProvider` for tests/local dev
- [ ] `BankTransferQrProvider` for generic Vietnamese bank-transfer instructions
- [ ] `CoreBankPaymentProvider` for end-to-end demo with `corebank-api`
- [ ] Provider routing by selected bank/provider, never by hardcoded bank name in service logic

### Bank catalog

- [ ] `GET /api/v1/payment-banks`
- [ ] Seed active Vietnamese bank catalog records
- [ ] Include `CoreBank Demo Bank` as a selectable bank
- [ ] Bank records include code, short name, full name, BIN if available, method, provider, active flag, display order
- [ ] Frontend must fetch bank list from backend, not hardcode bank constants

### Payment APIs

- [ ] `POST /api/v1/bookings/{id}/payments/authorize` — idempotency required
- [ ] `GET /api/v1/bookings/{id}/payments`
- [ ] `POST /api/v1/payments/{paymentId}/capture` — idempotency required
- [ ] `POST /api/v1/payments/{paymentId}/void` — idempotency required
- [ ] `POST /api/v1/payments/{paymentId}/refund` — idempotency required
- [ ] `GET /api/v1/payments/{paymentId}/reconciliation`

### CoreBank provider

- [ ] Map RentFlow booking/payment to CoreBank `externalOrderRef`
- [ ] Authorize via CoreBank `POST /api/payments/authorize-hold`
- [ ] Capture via CoreBank `POST /api/payments/capture-hold`
- [ ] Void via CoreBank `POST /api/payments/void-hold`
- [ ] Refund via CoreBank `POST /api/payments/refund`
- [ ] Reconcile via CoreBank `GET /api/payments/orders?externalOrderRef=...`
- [ ] Store CoreBank `paymentOrderId`, `holdId`, `journalId`, `refundJournalId`

### Idempotency scopes

- [ ] `AUTHORIZE_PAYMENT`
- [ ] `CAPTURE_PAYMENT`
- [ ] `VOID_PAYMENT`
- [ ] `REFUND_PAYMENT`
- [ ] `RECONCILE_PAYMENT`

Provider idempotency key format:

```text
rentflow:{operation}:{bookingOrPaymentId}:{clientIdempotencyKey}
```

Examples:

```text
rentflow:authorize:{bookingId}:{key}
rentflow:capture:{paymentId}:{key}
rentflow:void:{paymentId}:{key}
rentflow:refund:{paymentId}:{key}
```

## Must Not Implement

- [ ] Direct integration with every individual Vietnamese bank API in Phase 06
- [ ] Real NAPAS/VietQR aggregator settlement integration in Phase 06
- [ ] Webhook receiver for real banks in Phase 06
- [ ] Host approval/rejection workflow beyond current booking rule
- [ ] Kafka publishing before DB outbox exists
- [ ] Provider-specific logic inside controller or booking service
- [ ] Mark generic QR transfer as paid just because a QR/instruction was generated

## Files/Modules Expected

```text
com.rentflow.payment/
├── controller/
│   ├── BookingPaymentController.java
│   ├── PaymentController.java
│   └── PaymentBankController.java
├── service/
│   ├── PaymentService.java
│   ├── PaymentBankService.java
│   ├── PaymentStateMachine.java
│   └── PaymentReconciliationService.java
├── provider/
│   ├── PaymentProvider.java
│   ├── PaymentProviderRouter.java
│   ├── stub/
│   │   └── StubPaymentProvider.java
│   ├── banktransfer/
│   │   └── BankTransferQrProvider.java
│   └── corebank/
│       ├── CoreBankPaymentProvider.java
│       ├── CoreBankPaymentClient.java
│       └── CoreBankPaymentProperties.java
├── entity/
│   ├── BookingPayment.java
│   ├── PaymentTransaction.java
│   └── PaymentBank.java
├── repository/
│   ├── BookingPaymentRepository.java
│   ├── PaymentTransactionRepository.java
│   └── PaymentBankRepository.java
├── dto/
│   ├── PaymentBankResponse.java
│   ├── AuthorizePaymentRequest.java
│   ├── AuthorizePaymentResponse.java
│   ├── PaymentDetailResponse.java
│   ├── CaptureRequest.java
│   ├── VoidRequest.java
│   ├── RefundRequest.java
│   └── ReconciliationResponse.java
└── mapper/
    └── PaymentMapper.java
```

## Database direction

### `payment_banks`

```text
id UUID PK
code VARCHAR(40) UNIQUE NOT NULL
bin VARCHAR(20) NULL
short_name VARCHAR(80) NOT NULL
full_name VARCHAR(255) NOT NULL
logo_url TEXT NULL
country_code VARCHAR(2) NOT NULL DEFAULT 'VN'
payment_method VARCHAR(40) NOT NULL
provider VARCHAR(40) NOT NULL
active BOOLEAN NOT NULL DEFAULT true
display_order INT NOT NULL DEFAULT 0
metadata JSONB NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

### `booking_payments`

```text
id UUID PK
booking_id UUID UNIQUE NOT NULL
selected_bank_id UUID NULL
payment_method VARCHAR(40) NOT NULL
provider VARCHAR(40) NOT NULL
status VARCHAR(40) NOT NULL
authorized_amount NUMERIC(12,2) NOT NULL DEFAULT 0
captured_amount NUMERIC(12,2) NOT NULL DEFAULT 0
refunded_amount NUMERIC(12,2) NOT NULL DEFAULT 0
currency VARCHAR(3) NOT NULL DEFAULT 'VND'
external_order_ref VARCHAR(128) UNIQUE NULL
provider_payment_order_id VARCHAR(120) NULL
provider_hold_id VARCHAR(120) NULL
provider_status VARCHAR(80) NULL
provider_metadata JSONB NULL
version INT NOT NULL DEFAULT 0
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

### `payment_transactions`

```text
id UUID PK
booking_payment_id UUID NOT NULL
booking_id UUID NOT NULL
type VARCHAR(20) NOT NULL
status VARCHAR(40) NOT NULL
amount NUMERIC(12,2) NOT NULL
currency VARCHAR(3) NOT NULL DEFAULT 'VND'
provider VARCHAR(40) NOT NULL
provider_request_id VARCHAR(120) NULL
provider_ref VARCHAR(120) NULL
provider_journal_id VARCHAR(120) NULL
provider_response JSONB NULL
provider_error_code VARCHAR(80) NULL
provider_error_message TEXT NULL
idempotency_key_id UUID NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

## API Contracts

### GET /api/v1/payment-banks

Response: `200 OK`

```json
{
  "items": [
    {
      "id": "uuid",
      "code": "VCB",
      "bin": "970436",
      "shortName": "Vietcombank",
      "fullName": "Joint Stock Commercial Bank for Foreign Trade of Vietnam",
      "paymentMethod": "BANK_TRANSFER_QR",
      "provider": "VIETQR_MANUAL",
      "active": true
    },
    {
      "id": "uuid",
      "code": "COREBANK",
      "bin": null,
      "shortName": "CoreBank Demo Bank",
      "fullName": "CoreBank Demo Bank",
      "paymentMethod": "COREBANK_TRANSFER",
      "provider": "COREBANK",
      "active": true
    }
  ]
}
```

### POST /api/v1/bookings/{id}/payments/authorize

Headers: `Idempotency-Key: uuid`

Request:

```json
{
  "bankId": "uuid",
  "paymentMethod": "COREBANK_TRANSFER"
}
```

Response for CoreBank path: `200 OK`

```json
{
  "booking": {
    "id": "uuid",
    "status": "CONFIRMED",
    "pickupDate": "2026-06-01",
    "returnDate": "2026-06-03",
    "totalAmount": 1400000,
    "currency": "VND"
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

Response for generic bank transfer path: `200 OK`

```json
{
  "booking": {
    "id": "uuid",
    "status": "HELD"
  },
  "payment": {
    "id": "uuid",
    "status": "PENDING_TRANSFER",
    "paymentMethod": "BANK_TRANSFER_QR",
    "provider": "VIETQR_MANUAL",
    "externalOrderRef": "rentflow:booking:uuid",
    "authorizedAmount": 0,
    "capturedAmount": 0,
    "refundedAmount": 0,
    "currency": "VND",
    "transferInstruction": {
      "bankCode": "VCB",
      "bankBin": "970436",
      "accountNumber": "configured-beneficiary-account",
      "accountName": "RENTFLOW ESCROW",
      "amount": 1400000,
      "content": "RENTFLOW uuid",
      "qrPayload": "provider-or-locally-generated-payload"
    }
  }
}
```

### POST /api/v1/payments/{paymentId}/capture

Headers: `Idempotency-Key: uuid`

Request:

```json
{
  "amount": 700000
}
```

CoreBank path stores provider `journalId` from `capture-hold` response.

### POST /api/v1/payments/{paymentId}/void

Headers: `Idempotency-Key: uuid`

CoreBank path calls `void-hold` using stored provider hold id.

### POST /api/v1/payments/{paymentId}/refund

Headers: `Idempotency-Key: uuid`

Request:

```json
{
  "amount": 300000,
  "reason": "Customer cancellation refund"
}
```

CoreBank path calls `refund` using stored provider payment order id and stores `refundJournalId`.

### GET /api/v1/payments/{paymentId}/reconciliation

Response includes local state, provider state, and mismatch flags.

## TX-02A: CoreBank Authorize Payment

Recommended external-call-safe transaction pattern:

```text
TX1 RentFlow:
1. Lock idempotency key (scope=AUTHORIZE_PAYMENT) FOR UPDATE
2. Lock booking row FOR UPDATE
3. Validate booking.status = HELD
4. Validate booking.holdExpiresAt > now
5. Lock/create booking_payments row FOR UPDATE
6. Create payment_transaction AUTHORIZE/PENDING
7. Commit

External call:
8. Call CoreBank authorize-hold with deterministic provider idempotency key

TX2 RentFlow:
9. Lock booking row FOR UPDATE
10. Lock booking_payments row FOR UPDATE
11. Lock availability rows [pickupDate, returnDate) FOR UPDATE ORDER BY available_date ASC
12. Validate all availability: status = HOLD AND booking_id = current booking
13. Store CoreBank paymentOrderId, holdId, provider status, response JSON
14. Create/mark payment_transaction AUTHORIZE/SUCCEEDED
15. If listing.instantBook = true:
      booking -> CONFIRMED
      availability HOLD -> BOOKED
      booking_payment -> AUTHORIZED
    Else:
      booking -> PENDING_HOST_APPROVAL
      hostApprovalExpiresAt = now + 24 hours
      booking_payment -> AUTHORIZED
16. Commit
```

If external call succeeds but TX2 fails:

```text
- mark payment transaction COMPENSATION_REQUIRED
- attempt CoreBank void-hold
- mark payment RECONCILIATION_REQUIRED if compensation fails
```

## TX-02B: Generic Bank Transfer Instruction

```text
1. Lock idempotency key
2. Lock booking
3. Validate booking.status = HELD
4. Create booking_payment with PENDING_TRANSFER
5. Generate transfer instruction
6. Do not change availability HOLD -> BOOKED yet
7. Return transfer instruction
```

Confirmation of generic bank transfer is out of Phase 06 unless a real provider/webhook/reconciliation source exists.

## Payment Status Transitions

| Current Status | Action | New Status |
|---|---|---|
| UNPAID | Generate bank transfer instruction | PENDING_TRANSFER |
| UNPAID | CoreBank AUTHORIZE succeeds | AUTHORIZED |
| UNPAID | AUTHORIZE fails | FAILED |
| PENDING_TRANSFER | Manual/provider confirmation | AUTHORIZED |
| AUTHORIZED | CAPTURE full | CAPTURED |
| AUTHORIZED | CAPTURE partial | AUTHORIZED |
| AUTHORIZED | VOID | VOIDED |
| CAPTURED | REFUND full | REFUNDED |
| CAPTURED | REFUND partial | PARTIALLY_REFUNDED |
| PARTIALLY_REFUNDED | REFUND remaining | REFUNDED |
| Any provider drift | Reconciliation mismatch | RECONCILIATION_REQUIRED |

## Acceptance Criteria

- [ ] Customer can list/select active payment banks
- [ ] `CoreBank Demo Bank` appears in the same bank selector as real Vietnamese banks
- [ ] Generic Vietnamese bank transfer path returns transfer instructions and does not falsely mark money as authorized
- [ ] CoreBank authorize creates provider hold and stores paymentOrderId/holdId
- [ ] HELD booking -> CONFIRMED after CoreBank authorization for instant booking
- [ ] HELD booking -> PENDING_HOST_APPROVAL after CoreBank authorization for manual booking
- [ ] Availability HOLD -> BOOKED after successful CoreBank authorization for instant booking
- [ ] CoreBank capture stores journalId
- [ ] CoreBank void restores provider hold and marks local payment VOIDED
- [ ] CoreBank refund stores refundJournalId and updates local refunded amount/status
- [ ] Payment idempotency: same key + same body = same response
- [ ] Payment idempotency: same key + different body = 409
- [ ] CoreBank reconciliation by externalOrderRef detects matching state
- [ ] Reconciliation mismatch marks payment RECONCILIATION_REQUIRED
- [ ] Provider-specific logic stays behind PaymentProvider implementations

## Tests Required

- [ ] Unit: Payment provider router selects provider by bank provider field
- [ ] Unit: Payment state machine transitions
- [ ] Unit: CoreBank request mapping includes externalOrderRef
- [ ] Unit: CoreBank response mapping stores paymentOrderId/holdId/journal ids
- [ ] Unit: BankTransferQrProvider creates instruction without authorizing money
- [ ] Unit: Over-capture rejected
- [ ] Unit: Over-refund rejected
- [ ] Integration: `GET /api/v1/payment-banks` returns seeded banks including CoreBank
- [ ] Integration: CoreBank authorize -> booking CONFIRMED (instant)
- [ ] Integration: CoreBank authorize -> booking PENDING_HOST_APPROVAL (manual)
- [ ] Integration: CoreBank capture stores provider journal id
- [ ] Integration: CoreBank void marks local VOIDED
- [ ] Integration: CoreBank refund marks local PARTIALLY_REFUNDED/REFUNDED
- [ ] Integration: CoreBank reconciliation by externalOrderRef
- [ ] Integration: Generic bank transfer returns instruction and keeps payment PENDING_TRANSFER
- [ ] Integration: Authorize idempotency -> same response
- [ ] Integration: Capture idempotency -> same response
- [ ] Integration: Void idempotency -> same response
- [ ] Integration: Refund idempotency -> same response
- [ ] Concurrency: authorize and cancel same booking remain consistent
- [ ] Concurrency: capture and refund same payment do not over-capture/over-refund

## Notes

- Phase 06 is no longer stub-only.
- Stub remains necessary for tests and offline local development.
- CoreBank is the real demo provider and financial source of truth.
- Generic Vietnamese banks are selectable through a catalog but may route to manual/QR instruction flow until real provider APIs/webhooks are added.
- RentFlow must not own financial truth. RentFlow orchestrates; providers execute money movement.
- Reconciliation is mandatory for any external provider path.
