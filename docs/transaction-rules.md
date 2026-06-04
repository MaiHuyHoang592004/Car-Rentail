# Transaction Rules — RentFlow

> Extract from SRS section 23. Each TX is a transactional boundary.

---

## TX-01 — Create Booking

```text
@Transactional
createBooking()
```

**Order:**

1. Lock/insert idempotency key by (userId, scope=CREATE_BOOKING, key).
2. If COMPLETED + same requestHash: return stored response.
3. If COMPLETED + different requestHash: return 409 IDEMPOTENCY_KEY_CONFLICT.
4. Validate customer, listing, vehicle, driver verification, date range.
5. Validate host_id != customer_id.
6. Lock availability rows using SELECT FOR UPDATE.
7. Validate all rows exist and status = FREE.
8. Insert booking with status HELD.
9. Update availability rows to HOLD.
10. Save idempotency response as COMPLETED.
11. Commit.

**Lock SQL:**

```sql
SELECT *
FROM availability_calendar
WHERE listing_id = :listingId
  AND available_date >= :pickupDate
  AND available_date < :returnDate
ORDER BY available_date ASC
FOR UPDATE;
```

---

## TX-02 — Authorize Payment

```text
@Transactional
authorizePayment()
```

**Order:**

1. Lock idempotency key with scope AUTHORIZE_PAYMENT using FOR UPDATE.
2. Lock booking row using FOR UPDATE.
3. Validate booking.status = HELD.
4. Validate booking.hold_expires_at > now.
5. Lock booking_payments row using FOR UPDATE.
6. Lock availability rows for [pickupDate, returnDate) using SELECT FOR UPDATE ordered by available_date ASC.
7. Validate all availability rows have: status = HOLD AND booking_id = current booking id.
8. Create payment transaction AUTHORIZE/SUCCEEDED.
9. If listing.instantBook = true:
   - booking -> CONFIRMED
   - availability HOLD -> BOOKED
   - booking_payment -> AUTHORIZED
10. If listing.instantBook = false:
    - booking -> PENDING_HOST_APPROVAL
    - host_approval_expires_at = now + 24 hours
    - booking_payment -> AUTHORIZED
11. Write booking timeline.
12. Write audit log.
13. Write outbox event.
14. Commit.

**Failure:**

```
If any row is not HOLD or belongs to another booking:
409 BOOKING_INVALID_STATUS
```

---

## TX-03 — Expire HELD Booking

```text
1. Query expired HELD bookings with FOR UPDATE SKIP LOCKED.
2. Process in batches of 100.
3. For each booking:
   - lock booking
   - lock availability rows
   - re-check status = HELD
   - booking -> EXPIRED
   - availability HOLD -> FREE
   - timeline/outbox
4. Commit per batch.
```

**SQL:**

```sql
SELECT *
FROM bookings
WHERE status = 'HELD'
  AND hold_expires_at < :now
ORDER BY id
LIMIT :batchSize
FOR UPDATE SKIP LOCKED;
```

---

## TX-04 — Cancel Booking

```text
1. Lock idempotency key scope CANCEL_BOOKING.
2. Lock booking FOR UPDATE.
3. Validate actor permission.
4. Validate status allows cancellation.
5. Lock booking_payment if exists.
6. Lock availability rows.
7. Apply cancellation policy.
8. Create pending CAPTURE/VOID payment transaction rows as needed.
9. Commit prepare transaction.
10. Call provider outside the DB transaction.
11. Re-lock booking/payment/availability and revalidate prepared state.
12. Mark payment transaction success/failure.
13. booking -> CANCELLED.
14. Release availability if before check-in.
15. Write audit/timeline/outbox.
16. Commit finalize transaction.
```

Provider calls now run outside the DB transaction using `prepare -> provider call -> finalize`.
Partial-penalty cancellation finalizes CAPTURE before attempting VOID, so local drift after capture stops the flow before another provider call. ✅

---

## TX-05 — Cancel Booking with Partial Penalty

```text
@Transactional
cancelBookingWithPenalty()
```

**Order:**

1. Lock idempotency key scope CANCEL_BOOKING.
2. Lock booking row FOR UPDATE.
3. Lock booking_payments row FOR UPDATE.
4. Lock availability rows FOR UPDATE.
5. Validate booking.status = CONFIRMED.
6. Validate payment.status = AUTHORIZED.
7. Calculate refundPercent and penaltyAmount.
8. If penaltyAmount > 0:
   - prepare CAPTURE/PENDING.
9. Prepare VOID/PENDING for remaining authorization.
10. Commit prepare transaction.
11. CAPTURE penaltyAmount outside DB TX.
12. Re-lock and finalize CAPTURE before attempting VOID.
13. VOID remaining authorization outside DB TX.
14. Re-lock and finalize booking/payment/availability cancellation.
15. Write audit/timeline/outbox.
16. Commit finalize transaction.

**Failure handling:**

```
CAPTURE fails:
    rollback all
    return PAYMENT_FAILED

VOID fails after CAPTURE succeeds:
    record failed VOID transaction
    create retry task/outbox event
    keep cancellation intent
    alert admin/operator
    return success with warning flag
```

**Critical: CAPTURE before VOID (BR-47)**

Provider calls now run outside the DB transaction. Finalization drift returns `PAYMENT_FINALIZATION_UNSAFE` and does not overwrite local state. ✅

---

## TX-06 — Host Approve/Reject

### Host Approval

```text
1. Lock idempotency key scope HOST_APPROVE_BOOKING.
2. Lock booking FOR UPDATE.
3. Validate host owns booking.
4. Validate status = PENDING_HOST_APPROVAL.
5. Lock availability rows FOR UPDATE.
6. booking -> CONFIRMED.
7. availability HOLD -> BOOKED.
8. timeline/audit/outbox.
```

**Lock order: idempotency → booking → payment → availability ✅**
No provider call needed. ✅

### Host Rejection

```text
1. Lock idempotency key scope HOST_REJECT_BOOKING.
2. Lock booking FOR UPDATE.
3. Validate host owns booking.
4. Validate status = PENDING_HOST_APPROVAL.
5. Lock payment row FOR UPDATE.
6. Lock availability rows FOR UPDATE.
7. VOID authorization.
8. booking -> REJECTED.
9. availability HOLD -> FREE.
10. timeline/audit/outbox.
```

**Lock order: idempotency → booking → payment → availability ✅**
Provider call now runs outside the DB transaction using `prepare -> provider call -> finalize`.
Finalize re-locks and revalidates booking/payment/availability before mutating local state. ✅

---

## TX-07 — Payment State Mutation

All payment mutations must:

```text
1. Lock booking row FOR UPDATE.
2. Lock booking_payments row FOR UPDATE.
3. Validate transition.
4. Ensure captured_amount + captureAmount <= authorized_amount.
5. Ensure refunded_amount + refundAmount <= captured_amount.
6. Create payment_transactions row.
7. Update booking_payments aggregate.
8. Commit.
```

---

## TX-08 — Driver Verification Expiry Job

```text
1. Find driver_verifications where status IN ('PENDING', 'APPROVED')
   and license_expiry_date < current_date.
2. Process in batches of 100 with FOR UPDATE SKIP LOCKED if needed.
3. For each row:
   - lock row FOR UPDATE
   - update driver_verifications.status = EXPIRED
   - update user_profiles.driver_verification_status = EXPIRED
   - create notification
   - create audit log/system event
4. Existing bookings remain unchanged.
```

---

## TX-09 — Listing Submit

Use atomic update:

```sql
UPDATE listings
SET status = 'PENDING_APPROVAL',
    updated_at = now()
WHERE id = :id
  AND host_id = :authUserId
  AND status = 'DRAFT'
RETURNING id;
```

If no row returned: 409 BOOKING_INVALID_STATUS

---

## TX-10 — Vehicle Archive

```text
1. Lock vehicle row FOR UPDATE.
2. Validate vehicle.host_id = authUserId.
3. Query all listings for this vehicle.
4. Query bookings for all related listings.
5. If any booking status is HELD, CONFIRMED, or IN_PROGRESS:
   - reject with 409 VEHICLE_ARCHIVE_NOT_ALLOWED.
6. If all related bookings are terminal:
   - archive all non-ARCHIVED listings first.
7. vehicle.status -> ARCHIVED.
8. Write audit log.
9. Commit.

All listing archive operations and vehicle archive occur in the same transaction.
```

---

## TX-11 — Vehicle Suspend / Maintenance

```text
1. Lock vehicle row FOR UPDATE.
2. Validate transition is allowed.
3. vehicle.status -> SUSPENDED or MAINTENANCE.
4. ACTIVE listings for vehicle -> SUSPENDED.
5. Existing CONFIRMED/IN_PROGRESS bookings unchanged.
6. Notify host/affected customers if needed.
7. audit/outbox.
8. commit.
```

---

## TX-12 — Booking Patch

```text
1. Lock booking row FOR UPDATE.
2. Validate customer owns booking.
3. Validate status in HELD, PENDING_HOST_APPROVAL, CONFIRMED.
4. Update only pickupLocation and/or returnLocation.
5. Write timeline/audit if values changed.
6. commit.
```

---

## Idempotency Concurrency

Idempotency row must be locked with `FOR UPDATE`.

```sql
SELECT *
FROM idempotency_keys
WHERE user_id = :userId
  AND scope = :scope
  AND key = :key
FOR UPDATE;
```

**Behavior:**

```
PROCESSING + locked_until > now:
    409 REQUEST_ALREADY_PROCESSING

PROCESSING + locked_until <= now:
    retry allowed after row lock

COMPLETED + same requestHash:
    return stored response

COMPLETED + different requestHash:
    409 IDEMPOTENCY_KEY_CONFLICT

FAILED:
    retry allowed if lock expired
```

---

## Lock-Order Transaction Matrix (Slice 1)

### Canonical Invariant

```
1. Idempotency key (FOR UPDATE) — always first (where applicable)
2. Booking row (FOR UPDATE)
3. BookingPayment row (FOR UPDATE)
4. Availability rows (ORDER BY available_date ASC, FOR UPDATE) — always last
```

### Compliance Matrix

| Operation | File/Method | Lock Order | Provider Call Outside TX? | Risk |
|---|---|---|---|---|
| **Authorize** | `CoreBankAuthorizeService.authorizeBookingPayment()` | ✅ idempotency → booking → payment → availability | ✅ Yes (split TX pattern) | LOW |
| **Capture** | `CoreBankCaptureService.capture()` | ✅ booking → payment (prepare + finalize) | ✅ Yes (between prepare/finalize) | LOW |
| **Void** | `CoreBankVoidService.voidAuthorization()` | ✅ booking → payment (prepare + finalize) | ✅ Yes (between prepare/finalize) | LOW |
| **Refund** | `CoreBankRefundService.refund()` | ✅ booking → payment (prepare + finalize) | ✅ Yes (between prepare/finalize) | LOW |
| **Cancel booking** | `BookingService.cancelBooking()` | ✅ booking → payment → availability | ✅ Yes (split TX pattern) | LOW |
| **Host approve** | `HostBookingApprovalService.approveBooking()` | ✅ booking → payment → availability | ✅ No provider call | OK |
| **Host reject** | `HostBookingApprovalService.rejectBooking()` | ✅ booking → payment → availability | ✅ Yes (split TX pattern) | LOW |
| **Void retry** | `DefaultPaymentVoidRetryService.retrySingle()` | ✅ payment lock only; prepare/finalize relock before mutation | ✅ Yes (between prepare/finalize) | LOW |
| **Trip checkout** | `TripPaymentCaptureService.captureRemainingForBooking()` | ✅ payment lock only; prepare/finalize relock before mutation | ✅ Yes (between prepare/finalize) | LOW |
| **Create booking** | `BookingService.createBooking()` | ✅ idempotency → availability | ✅ No provider call | OK |
| **Expire HELD** | `BookingExpiryJob` | ✅ booking → availability | ✅ No provider call | OK |
| **Expire host approval** | `ExpireHostApprovalsProcessor` | ✅ booking → payment → availability | ✅ Yes (split TX pattern) | LOW |

### Violation Summary

No known provider-call-while-holding-locks violation remains in the listed payment, booking, or host-approval mutation paths.

All payment/host-approval mutation paths listed in this matrix now follow:

```text
prepare (lock + validate + create PENDING payment_transaction)
-> provider call outside DB TX
-> finalize (re-lock + revalidate + mutate aggregate + mark transaction)
```

If provider success is followed by local state drift, finalize must:

```text
1. NOT overwrite booking/payment/availability state
2. mark payment_transaction FAILED
3. set provider_error_code = PAYMENT_FINALIZATION_UNSAFE
4. surface a domain error for reconciliation / operator review
```

---

## Critical Rules Summary

| Rule | Description |
|---|---|
| Lock order | Always lock by `available_date ASC` to prevent deadlocks |
| Hold expiry | HELD bookings expire after 15 minutes |
| Approval expiry | PENDING_HOST_APPROVAL expires after 24 hours |
| CAPTURE before VOID | For partial penalty cancellation |
| FOR UPDATE SKIP LOCKED | For all scheduled jobs to prevent duplicate processing |
| Bounded batches | All jobs process in batches (default 100) |
| Same TX for state change + outbox | Outbox events created in same transaction as business state change |
| Provider calls must be outside DB TX | Do not call external providers while holding DB locks (exceptions must be explicitly justified) |
| Lock order: booking before payment | Both `prepare*` and `finalize*` methods must lock booking before payment |
