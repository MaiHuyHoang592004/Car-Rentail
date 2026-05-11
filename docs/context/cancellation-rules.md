# Cancellation Rules

## TX-04 Cancel HELD (no payment)

1. Lock idempotency key `CANCEL_BOOKING` FOR UPDATE
2. Lock booking FOR UPDATE
3. Validate actor permission
4. Validate `status = HELD`
5. Lock availability rows FOR UPDATE
6. booking → CANCELLED
7. availability HOLD → FREE
8. Timeline + Audit + Outbox
9. Commit

## TX-05 Cancel CONFIRMED with Penalty

1. Lock idempotency key `CANCEL_BOOKING` FOR UPDATE
2. Lock booking FOR UPDATE
3. Lock booking_payments FOR UPDATE
4. Lock availability rows FOR UPDATE
5. Validate `status = CONFIRMED`, `payment.status = AUTHORIZED`
6. Calculate `refundPercent` and `penaltyAmount = totalAmount × (1 - refundPercent)`
7. If `penaltyAmount > 0`: CAPTURE penaltyAmount
8. VOID remaining authorization
9. Update booking_payments: `captured += penaltyAmount`, `authorized = 0`, `status = CAPTURED`
10. booking → CANCELLED
11. availability → FREE
12. Timeline + Audit + Outbox
13. Commit

## FLEXIBLE Refund Table

| Condition | Refund |
|---|---|
| Cancel ≥ 24h before pickup | 100% |
| Cancel < 24h before pickup | 80% |
| Cancel after check-in | No auto cancel; dispute required |

## MODERATE Refund Table

| Condition | Refund |
|---|---|
| Cancel ≥ 72h before pickup | 100% |
| Cancel 24–72h before pickup | 50% |
| Cancel < 24h before pickup | 0% |
| Cancel after check-in | No auto cancel; dispute required |

## STRICT Refund Table

| Condition | Refund |
|---|---|
| Cancel ≥ 7 days before pickup | 100% |
| Cancel < 7 days before pickup | 0% |
| Cancel after check-in | No auto cancel; dispute required |

## ⚠ BR-47: CAPTURE before VOID (Critical)

**Always CAPTURE first, then VOID the remainder.**
Never VOID before CAPTURE. Doing VOID first eliminates the authorization before capturing the penalty.

## VOID Failure Recovery Path

When CAPTURE succeeds but VOID fails:
1. Record failed VOID transaction
2. Create outbox event for VOID retry
3. Alert admin/operator
4. Return **202 PAYMENT_VOID_RETRY_REQUIRED** with `cancellationCompleted=true, voidRetryRequired=true`

The cancellation is still complete — the penalty was captured. Only the void retry is pending.

## cancellation_reason

- Max 500 characters
- Strip all HTML tags server-side before storage
- No auto-cancel after check-in — dispute required
