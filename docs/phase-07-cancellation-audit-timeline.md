# Phase 07 — Cancellation + Audit + Timeline

## Goal

Implement cancellation policy calculator, full cancellation flow with payment handling, booking timeline, and audit logging.

## Slice Status (2026-05-29)

- [x] Slice 7.2R — Void-retry reliability persistence
  - Cancellation paths that fail at VOID now persist retry metadata (`voidRetryRequired`, `voidRetryCount`, `voidRetryNextAt`, `voidRetryLastError`).
  - Same idempotency-key replay returns `202 PAYMENT_VOID_RETRY_REQUIRED` without re-invoking capture/void side effects.
- [x] Slice 7.4 — Audit sanitization coverage
  - `DefaultAuditLogService` now redacts sensitive fields in JSON `details` payload before persistence.
  - Protected keys include password/token hashes, encrypted identifiers, and provider payment references.
- [x] Slice 7.5 — Outbox completeness (listing admin decisions)
  - `LISTING_APPROVED` and `LISTING_REJECTED` outbox events are now written in the same transaction as state change.
  - Integration coverage verifies outbox rows for approve/reject flows.

## Must Implement

- [ ] CancellationPolicyCalculator: FLEXIBLE, MODERATE, STRICT policies
- [ ] Refund percentage lookup based on hours before pickup
- [ ] Full cancellation flow: cancel HELD, cancel PENDING_HOST_APPROVAL, cancel CONFIRMED
- [ ] Cancellation with full refund (VOID before capture)
- [ ] Cancellation with partial penalty (CAPTURE penalty THEN VOID remaining)
- [ ] Cancellation: VOID failure recovery path
- [ ] cancellation_reason sanitization (strip HTML, max 500 chars)
- [ ] `POST /api/v1/bookings/{id}/cancel` — full implementation
- [ ] Idempotency scope: CANCEL_BOOKING
- [ ] `BookingTimeline` entity + repository
- [ ] `TimelineService`: addEntry(bookingId, actorId, actorType, eventType, message, metadata)
- [ ] Timeline entries for all booking status changes
- [ ] `AuditLog` entity + repository
- [ ] `AuditService`: log with sanitized before/after values
- [ ] Audit entries for: booking created, booking cancelled, booking confirmed, payment authorized, payment captured, listing approved, listing rejected, vehicle archived
- [ ] Sanitized data: never log password_hash, token_hash, plate_number_encrypted, vin_encrypted, license_number_encrypted, payment-sensitive data
- [ ] Outbox event creation in same transaction as state change
- [ ] `OutboxEvent` entity + repository
- [ ] Outbox event types: BookingHeld, BookingConfirmed, BookingCancelled, BookingExpired, PaymentAuthorized, PaymentCaptured, PaymentVoided, PaymentRefunded

## Must Not Implement

- [ ] Driver verification
- [ ] Rate limiting
- [ ] Trip check-in/check-out
- [ ] Reviews
- [ ] Disputes
- [ ] Outbox scheduler / event publishing (P9)
- [ ] Kafka integration

## Files/Modules Expected

```
com.rentflow.booking/
├── service/
│   ├── BookingCancelService.java      (enhanced)
│   └── CancellationPolicyCalculator.java
├── entity/
│   └── BookingTimeline.java
├── repository/
│   └── BookingTimelineRepository.java
└── dto/
    └── CancelBookingRequest.java

com.rentflow.audit/
├── service/
│   └── AuditService.java
├── entity/
│   └── AuditLog.java
└── repository/
    └── AuditLogRepository.java

com.rentflow.notification/
├── service/
│   └── NotificationService.java
└── dto/
    └── NotificationResponse.java

com.rentflow.outbox/
├── entity/
│   └── OutboxEvent.java
├── repository/
│   └── OutboxEventRepository.java
└── service/
    └── OutboxEventPublisher.java    (stub - P9)
```

## Cancellation Policies

### FLEXIBLE

| Condition | Refund |
|---|---|
| Cancel at least 24 hours before pickup | 100% |
| Cancel less than 24 hours before pickup | 80% |
| Cancel after check-in | No automatic cancellation; dispute required |

### MODERATE

| Condition | Refund |
|---|---|
| Cancel at least 72 hours before pickup | 100% |
| Cancel 24-72 hours before pickup | 50% |
| Cancel less than 24 hours before pickup | 0% |
| Cancel after check-in | No automatic cancellation; dispute required |

### STRICT

| Condition | Refund |
|---|---|
| Cancel at least 7 days before pickup | 100% |
| Cancel less than 7 days before pickup | 0% |
| Cancel after check-in | No automatic cancellation; dispute required |

## Cancellation Scenarios

| Booking/Payment State | System Behavior |
|---|---|
| HELD, no payment | CANCELLED, availability FREE |
| PENDING_HOST_APPROVAL, AUTHORIZED | CANCELLED, VOID authorization, availability FREE |
| CONFIRMED, AUTHORIZED, refund 100% | VOID full authorized_amount, CANCELLED, availability FREE |
| CONFIRMED, AUTHORIZED, refund < 100% | CAPTURE penalty, VOID remaining, CANCELLED, availability FREE |

## TX-04 + TX-05: Cancel Booking Transaction

### Scenario: HELD (no payment)

```
@Transactional
cancelBooking_HELD() {
  1. Lock idempotency key (CANCEL_BOOKING) FOR UPDATE
  2. Lock booking FOR UPDATE
  3. Validate actor permission
  4. Validate status = HELD
  5. Lock availability rows FOR UPDATE
  6. booking -> CANCELLED
  7. availability HOLD -> FREE
  8. Timeline: BOOKING_CANCELLED
  9. Audit: BOOKING_CANCELLED
  10. Commit
}
```

### Scenario: CONFIRMED with AUTHORIZED payment (full refund)

```
@Transactional
cancelBooking_FullRefund() {
  1. Lock idempotency key (CANCEL_BOOKING) FOR UPDATE
  2. Lock booking FOR UPDATE
  3. Lock booking_payments FOR UPDATE
  4. Lock availability rows FOR UPDATE
  5. Validate status = CONFIRMED
  6. Validate payment.status = AUTHORIZED
  7. Calculate refundPercent = 100%
  8. VOID full authorized_amount
  9. Update booking_payments: status = VOIDED, authorized_amount = 0
  10. booking -> CANCELLED
  11. availability -> FREE
  12. Timeline: BOOKING_CANCELLED
  13. Audit: BOOKING_CANCELLED
  14. Commit
}
```

### Scenario: CONFIRMED with AUTHORIZED payment (partial penalty)

```
@Transactional
cancelBooking_PartialPenalty() {
  1. Lock idempotency key (CANCEL_BOOKING) FOR UPDATE
  2. Lock booking FOR UPDATE
  3. Lock booking_payments FOR UPDATE
  4. Lock availability rows FOR UPDATE
  5. Validate status = CONFIRMED
  6. Validate payment.status = AUTHORIZED
  7. Calculate refundPercent and penaltyAmount
     - penaltyAmount = totalAmount × (1 - refundPercent)
  8. If penaltyAmount > 0:
       CAPTURE penaltyAmount
  9. VOID remaining authorization
  10. Update booking_payments:
        captured_amount += penaltyAmount
        authorized_amount = 0
        status = CAPTURED
  11. booking -> CANCELLED
  12. availability -> FREE
  13. Timeline: BOOKING_CANCELLED
  14. Audit: BOOKING_CANCELLED
  15. Commit
}

CRITICAL ORDER: CAPTURE first, THEN VOID (BR-47)

VOID failure after CAPTURE succeeds:
  - record failed VOID transaction
  - create retry task/outbox event
  - alert admin/operator
  - return 202 PAYMENT_VOID_RETRY_REQUIRED with cancellationCompleted=true, voidRetryRequired=true
```

## Timeline Events

| Event | Actor | Message |
|---|---|---|
| BOOKING_HELD | USER | "Booking created, awaiting payment authorization" |
| BOOKING_CONFIRMED | USER/SYSTEM | "Booking confirmed" |
| BOOKING_PENDING_APPROVAL | USER | "Awaiting host approval" |
| BOOKING_APPROVED | USER | "Host approved the booking" |
| BOOKING_REJECTED | USER | "Host rejected the booking" |
| BOOKING_CANCELLED | USER/ADMIN | "Booking cancelled. Reason: {reason}" |
| BOOKING_EXPIRED | SYSTEM | "Booking hold expired" |
| BOOKING_IN_PROGRESS | USER | "Customer picked up the car" |
| BOOKING_COMPLETED | USER | "Trip completed" |
| PAYMENT_AUTHORIZED | USER | "Payment authorized: {amount}" |
| PAYMENT_CAPTURED | USER | "Payment captured: {amount}" |
| PAYMENT_VOIDED | USER/SYSTEM | "Payment voided" |
| PAYMENT_REFUNDED | USER | "Payment refunded: {amount}" |

## Acceptance Criteria

- [ ] Cancel HELD booking -> CANCELLED, availability FREE
- [ ] Cancel PENDING_HOST_APPROVAL -> VOID auth, CANCELLED, availability FREE
- [ ] Cancel CONFIRMED with 100% refund -> VOID auth, CANCELLED, availability FREE
- [ ] Cancel CONFIRMED with 80% refund -> CAPTURE 20%, VOID 80%, CANCELLED
- [ ] Cancel CONFIRMED with 50% refund -> CAPTURE 50%, VOID 50%, CANCELLED
- [ ] Cancel CONFIRMED with 0% refund -> CAPTURE 100%, CANCELLED
- [ ] Cancellation ordering: CAPTURE before VOID (BR-47)
- [ ] VOID failure after CAPTURE -> retry path created, admin alerted
- [ ] cancellation_reason sanitized (HTML stripped, max 500 chars)
- [ ] Timeline shows all booking status changes
- [ ] Audit log captures all important actions
- [ ] Audit log sanitized (no sensitive data)
- [ ] Outbox events created in same transaction
- [ ] Customer cancellation rejected after check-in
- [ ] IN_PROGRESS booking cannot be cancelled normally

## Tests Required

- [ ] Unit: FLEXIBLE policy 100% refund (>= 24h)
- [ ] Unit: FLEXIBLE policy 80% refund (< 24h)
- [ ] Unit: MODERATE policy 100%/50%/0% refund
- [ ] Unit: STRICT policy 100%/0% refund
- [ ] Unit: CancellationReason sanitization
- [ ] Unit: CancellationReason max 500 chars
- [ ] Integration: Cancel HELD -> CANCELLED, availability FREE
- [ ] Integration: Cancel CONFIRMED full refund -> VOID, CANCELLED
- [ ] Integration: Cancel CONFIRMED partial penalty -> CAPTURE, VOID, CANCELLED
- [ ] Integration: CAPTURE before VOID ordering verified
- [ ] Integration: VOID failure after CAPTURE -> retry created
- [ ] Integration: Cancel IN_PROGRESS -> rejected
- [ ] Integration: Timeline entries created for all status changes
- [ ] Integration: Audit log entries created
- [ ] Integration: Audit log sanitized
- [ ] Integration: Outbox events created in same TX

## Notes

- CRITICAL: For partial penalty, always CAPTURE first, then VOID remaining (BR-47).
- If VOID fails after CAPTURE succeeds, the cancellation is still complete but void retry is needed (BR-48).
- Use `@Transactional(propagation = REQUIRES_NEW)` carefully — prefer keeping everything in one transaction.
- cancellation_reason must be sanitized server-side before storage.
- Timeline actor_type: USER for human actors, SYSTEM for scheduler.
