# Booking Rules

## TX-01 Lock Order (11 steps)

1. Lock idempotency key `(userId, scope=CREATE_BOOKING, key)` FOR UPDATE
2. COMPLETED + same requestHash → return stored response
3. COMPLETED + different requestHash → 409 IDEMPOTENCY_KEY_CONFLICT
4. Validate customer, listing, vehicle, driver verification, date range
5. Validate `host_id != customer_id`
6. Lock availability rows `SELECT FOR UPDATE`
7. Validate all rows exist and `status = FREE`
8. Insert booking with `status = HELD`
9. Update availability rows to `HOLD`
10. Save idempotency response as COMPLETED
11. Commit

## Active Booking Statuses (overlap detection)

**Active:** `HELD`, `PENDING_HOST_APPROVAL`, `CONFIRMED`
**Not active:** `COMPLETED`, `CANCELLED`, `REJECTED`, `EXPIRED`

## Date Validation

- `[pickupDate, returnDate)` — returnDate is exclusive
- Min rental: 1 day. Max rental: 30 days
- Reject: `returnDate <= pickupDate` or `returnDate - pickupDate > 30 days`

## Idempotency Behavior (5 states × 2 hash scenarios)

| State | Same hash | Different hash |
|---|---|---|
| PROCESSING + lock active | 409 REQUEST_ALREADY_PROCESSING | 409 REQUEST_ALREADY_PROCESSING |
| PROCESSING + lock expired | retry allowed | retry allowed |
| COMPLETED | return stored response | 409 IDEMPOTENCY_KEY_CONFLICT |
| FAILED | retry if lock expired | retry if lock expired |
| (none/insert) | — | — |

## Availability Lock SQL

```sql
SELECT *
FROM availability_calendar
WHERE listing_id = :listingId
  AND available_date >= :pickupDate
  AND available_date < :returnDate
ORDER BY available_date ASC
FOR UPDATE;
```

## Expire HELD Job SQL

```sql
SELECT *
FROM bookings
WHERE status = 'HELD'
  AND hold_expires_at < :now
ORDER BY id
LIMIT :batchSize
FOR UPDATE SKIP LOCKED;
```

Batch size: 100. Multiple job instances must not process same row.
