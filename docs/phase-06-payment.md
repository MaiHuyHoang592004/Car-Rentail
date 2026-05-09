# Phase 06 — Payment Stub

## Goal

Implement the payment stub with authorize, void, capture (partial), refund, and payment idempotency. Connect payment to booking flow with TX-02.

## Must Implement

- [ ] `BookingPayment` entity + repository
- [ ] `PaymentTransaction` entity + repository
- [ ] Payment stub service (fake provider): authorize, capture, void, refund
- [ ] Payment stub: partial capture supported
- [ ] Payment stub: capture amount <= authorizedAmount - capturedAmount
- [ ] Payment stub: refund amount <= capturedAmount - refundedAmount
- [ ] `POST /api/v1/bookings/{id}/payments/authorize` — idempotency required
- [ ] `GET /api/v1/bookings/{id}/payments`
- [ ] `POST /api/v1/payments/{paymentId}/capture` — idempotency required
- [ ] `POST /api/v1/payments/{paymentId}/void` — idempotency required
- [ ] `POST /api/v1/payments/{paymentId}/refund` — idempotency required
- [ ] Idempotency scopes: AUTHORIZE_PAYMENT, CAPTURE_PAYMENT, VOID_PAYMENT, REFUND_PAYMENT
- [ ] TX-02: Authorize payment locks availability before HOLD -> BOOKED
- [ ] Instant booking: HELD -> CONFIRMED after authorization
- [ ] Manual booking (instantBook=false): HELD -> PENDING_HOST_APPROVAL after authorization
- [ ] Payment status machine: UNPAID -> AUTHORIZED -> CAPTURED -> REFUNDED
- [ ] Partial capture: multiple captures until full amount captured
- [ ] Payment state transitions lock booking_payments row
- [ ] Provider reference stored for each transaction

## Must Not Implement

- [ ] Real payment gateway integration
- [ ] Host approval/rejection (P1)
- [ ] Partial cancellation penalty (CAPTURE + VOID ordering — P7)
- [ ] Refund after cancellation penalty
- [ ] Kafka / outbox event publishing
- [ ] Webhook handlers

## Files/Modules Expected

```
com.rentflow.payment/
├── controller/
│   ├── BookingPaymentController.java
│   └── PaymentController.java
├── service/
│   ├── PaymentService.java
│   ├── PaymentStubService.java
│   └── PaymentStateMachine.java
├── entity/
│   ├── BookingPayment.java
│   └── PaymentTransaction.java
├── repository/
│   ├── BookingPaymentRepository.java
│   └── PaymentTransactionRepository.java
└── dto/
    ├── AuthorizeResponse.java
    ├── PaymentDetailResponse.java
    ├── CaptureRequest.java
    ├── VoidRequest.java
    └── RefundRequest.java
```

## API Contracts

### POST /api/v1/bookings/{id}/payments/authorize

Headers: `Idempotency-Key: uuid`

Response: `200 OK`
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
    "authorizedAmount": 1400000,
    "capturedAmount": 0,
    "refundedAmount": 0,
    "currency": "VND"
  }
}
```

### GET /api/v1/bookings/{id}/payments

Response: `200 OK`
```json
{
  "id": "uuid",
  "bookingId": "uuid",
  "status": "AUTHORIZED",
  "authorizedAmount": 1400000,
  "capturedAmount": 0,
  "refundedAmount": 0,
  "currency": "VND",
  "transactions": [
    {
      "id": "uuid",
      "type": "AUTHORIZE",
      "status": "SUCCEEDED",
      "amount": 1400000,
      "currency": "VND",
      "provider": "STUB",
      "providerRef": "stub-auth-123",
      "createdAt": "2026-05-09T11:05:00Z"
    }
  ]
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

### POST /api/v1/payments/{paymentId}/void

Headers: `Idempotency-Key: uuid`

### POST /api/v1/payments/{paymentId}/refund

Headers: `Idempotency-Key: uuid`

Request:
```json
{
  "amount": 300000
}
```

## TX-02: Authorize Payment Transaction

```
@Transactional
authorizePayment() {
  1. Lock idempotency key (scope=AUTHORIZE_PAYMENT) FOR UPDATE
  2. Lock booking row FOR UPDATE
  3. Validate booking.status = HELD
  4. Validate booking.holdExpiresAt > now
  5. Lock booking_payments row FOR UPDATE (create if UNPAID)
  6. Lock availability rows [pickupDate, returnDate) FOR UPDATE ORDER BY available_date ASC
  7. Validate all availability: status = HOLD AND booking_id = current booking
  8. Call paymentStub.authorize(totalAmount)
  9. Create payment_transaction AUTHORIZE/SUCCEEDED
  10. If listing.instantBook = true:
        booking -> CONFIRMED
        availability HOLD -> BOOKED
        booking_payment -> AUTHORIZED
    Else:
        booking -> PENDING_HOST_APPROVAL
        hostApprovalExpiresAt = now + 24 hours
        booking_payment -> AUTHORIZED
  11. Create booking_timeline entry (P1)
  12. Create audit log (P1)
  13. Create outbox event (P2)
  14. Commit
}

Failure:
  If any availability row not HOLD or belongs to another booking:
    409 BOOKING_INVALID_STATUS
```

## Payment Status Transitions

| Current Status | Action | New Status |
|---|---|---|
| UNPAID | AUTHORIZE succeeds | AUTHORIZED |
| UNPAID | AUTHORIZE fails | FAILED |
| AUTHORIZED | CAPTURE (full) | CAPTURED |
| AUTHORIZED | CAPTURE (partial) | AUTHORIZED (amount reduced) |
| AUTHORIZED | VOID | VOIDED |
| CAPTURED | REFUND (full) | REFUNDED |
| CAPTURED | REFUND (partial) | PARTIALLY_REFUNDED |
| PARTIALLY_REFUNDED | REFUND (remaining) | REFUNDED |
| AUTHORIZED | (capture fails) | stays AUTHORIZED, transaction FAILED |

## Partial Capture Rules

```
captureAmount <= authorizedAmount - capturedAmount
```

Example:
```
authorizedAmount = 1,000,000
capturedAmount = 0
First capture: 400,000 -> capturedAmount = 400,000, remaining = 600,000
Second capture: 600,000 -> capturedAmount = 1,000,000, status -> CAPTURED
```

## Acceptance Criteria

- [ ] HELD booking -> CONFIRMED after authorization (instant booking)
- [ ] HELD booking -> PENDING_HOST_APPROVAL after authorization (manual booking)
- [ ] Availability HOLD -> BOOKED after authorization
- [ ] Authorization reserves amount (does not capture)
- [ ] Partial capture works: multiple captures up to authorized amount
- [ ] Cannot capture more than authorized amount
- [ ] Cannot refund more than captured amount
- [ ] VOID releases remaining authorization
- [ ] Payment idempotency: same key + same body = same response
- [ ] Payment idempotency: same key + different body = 409
- [ ] TX-02 locks availability rows before state change
- [ ] No duplicate payment effect with same idempotency key
- [ ] Provider reference stored for each transaction

## Tests Required

- [ ] Unit: Payment state machine transitions
- [ ] Unit: Partial capture respects authorized amount
- [ ] Unit: Over-capture rejected
- [ ] Unit: Over-refund rejected
- [ ] Integration: Authorize -> booking CONFIRMED (instant)
- [ ] Integration: Authorize -> booking PENDING_HOST_APPROVAL (manual)
- [ ] Integration: Availability HOLD -> BOOKED after authorize
- [ ] Integration: Partial capture works
- [ ] Integration: Capture exceeds authorized -> 409
- [ ] Integration: VOID releases authorization
- [ ] Integration: Authorize idempotency -> same response
- [ ] Integration: Capture idempotency -> same response
- [ ] Integration: Void idempotency -> same response
- [ ] Integration: Refund idempotency -> same response
- [ ] Integration: Authorize twice with same key -> no duplicate effect
- [ ] Concurrency: Authorize and cancel same booking simultaneously -> consistent state
- [ ] Concurrency: Capture and cancel same payment -> no over-capture

## Notes

- Payment stub always succeeds for AUTHORIZE, VOID, REFUND. For CAPTURE, it always succeeds but validates the amount constraint.
- In production, replace `PaymentStubService` with real payment gateway integration.
- All payment mutations require Idempotency-Key.
- Store provider reference for audit and reconciliation.
- Payment module does not depend on booking service directly — use booking repository for reads.
