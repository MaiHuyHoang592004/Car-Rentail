# Payment Rules

## TX-02 Authorize Payment Lock Order (14 steps)

1. Lock idempotency key `(scope=AUTHORIZE_PAYMENT)` FOR UPDATE
2. Lock booking row FOR UPDATE
3. Validate `booking.status = HELD`
4. Validate `hold_expires_at > now`
5. Lock/create booking_payments row FOR UPDATE
6. Lock availability rows `[pickupDate, returnDate)` FOR UPDATE ORDER BY available_date ASC
7. Validate all availability: `status = HOLD` AND `booking_id = current booking`
8. Call `paymentStub.authorize(totalAmount)`
9. Create payment_transaction `AUTHORIZE/SUCCEEDED`
10. If `listing.instantBook = true`: booking → CONFIRMED, HOLD → BOOKED, payment → AUTHORIZED
11. If `listing.instantBook = false`: booking → PENDING_HOST_APPROVAL, `hostApprovalExpiresAt = now + 24h`, payment → AUTHORIZED
12. Write booking timeline
13. Write audit log
14. Write outbox event

**Failure:** Any availability row not HOLD or belongs to another booking → 409 BOOKING_INVALID_STATUS

## TX-07 Payment Mutation Rules

All mutations must:
1. Lock booking row FOR UPDATE
2. Lock booking_payments row FOR UPDATE
3. Validate transition
4. Ensure `captured + captureAmount <= authorized`
5. Ensure `refunded + refundAmount <= captured`
6. Create payment_transactions row
7. Update booking_payments aggregate

## Payment Status Machine

```
UNPAID → AUTHORIZED → CAPTURED → REFUNDED
                  ↘ VOIDED
        AUTHORIZED → PARTIALLY_REFUNDED → REFUNDED
```

Partial capture: multiple captures until full amount captured. Status stays AUTHORIZED until fully captured.

## Partial Capture Constraint

```
captureAmount <= authorizedAmount - capturedAmount
```

Example: authorized=1,000,000, captured=0 → first capture 400,000 → captured=400,000, remaining=600,000 → second capture 600,000 → captured=1,000,000, status → CAPTURED

## instantBook Behavior

- `instantBook = true` → after authorize: HELD → CONFIRMED, HOLD → BOOKED
- `instantBook = false` → after authorize: HELD → PENDING_HOST_APPROVAL, host approval expires in 24h
