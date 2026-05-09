# Phase 05 — Booking Core

## Goal

Implement create booking hold with idempotency, pessimistic locking, customer overlap detection, self-booking prevention, HELD status, and expire HELD job. **This is the portfolio core.**

## Must Implement

- [ ] `POST /api/v1/bookings` — requires Idempotency-Key header
- [ ] Idempotency service: resolve, computeRequestHash, createProcessingRecord, complete, fail
- [ ] Idempotency interceptor/filter to auto-resolve for mutation endpoints
- [ ] Canonical JSON hashing (SHA-256, keys sorted, UTF-8, null preserved)
- [ ] Idempotency scopes: CREATE_BOOKING
- [ ] Price calculation: base amount, extras (PER_DAY, PER_TRIP), total
- [ ] Price snapshot stored as JSONB at booking creation
- [ ] Policy snapshot stored as JSONB at booking creation
- [ ] SELECT FOR UPDATE on availability rows `[pickupDate, returnDate)` ORDER BY available_date ASC
- [ ] Customer overlap check: HELD, PENDING_HOST_APPROVAL, CONFIRMED statuses blocked
- [ ] Self-booking prevention: host cannot book own listing
- [ ] Driver verification gate: customer must have APPROVED status (with feature flag)
- [ ] Booking entity + repository
- [ ] Booking status: HELD
- [ ] hold_expires_at = now + 15 minutes
- [ ] Expire HELD booking scheduled job: every 1 minute, batch 100, FOR UPDATE SKIP LOCKED
- [ ] `GET /api/v1/bookings/me` — customer's bookings with pagination
- [ ] `GET /api/v1/bookings/{id}` — booking detail with role-based access
- [ ] `PATCH /api/v1/bookings/{id}` — update only pickupLocation and returnLocation
- [ ] `POST /api/v1/bookings/{id}/cancel` — basic cancel (no payment logic yet)
- [ ] Idempotency scopes for cancel
- [ ] Basic cancellation: HELD booking -> CANCELLED, availability FREE

## Must Not Implement

- [ ] Payment (authorize, capture, void, refund)
- [ ] Host approval flow
- [ ] Trip check-in/check-out
- [ ] Review
- [ ] Reports
- [ ] Kafka / outbox
- [ ] Partial cancellation penalty
- [ ] Audit logging (yet)
- [ ] Booking timeline (yet)

## Files/Modules Expected

```
com.rentflow.booking/
├── controller/
│   ├── BookingController.java
│   └── CustomerBookingController.java
├── service/
│   ├── BookingService.java
│   ├── BookingPriceCalculator.java
│   └── BookingCancelService.java
├── entity/
│   ├── Booking.java
│   └── BookingExtra.java
├── repository/
│   ├── BookingRepository.java
│   └── BookingExtraRepository.java
└── dto/
    ├── CreateBookingRequest.java
    ├── BookingResponse.java
    ├── UpdateBookingRequest.java
    └── CancelBookingRequest.java

com.rentflow.common.idempotency/
├── service/
│   ├── IdempotencyService.java
│   └── CanonicalJsonHasher.java
├── entity/
│   └── IdempotencyKey.java
├── repository/
│   └── IdempotencyKeyRepository.java
└── annotation/
    └── Idempotent.java

com.rentflow.scheduler/
├── ExpireHeldBookingsJob.java
└── DistributedLockService.java
```

## API Contracts

### POST /api/v1/bookings

Headers:
```
Idempotency-Key: 8b71f8d2-9e1d-4f7a-bbe6-334c3816df91
Authorization: Bearer eyJ...
```

Request:
```json
{
  "listingId": "uuid",
  "pickupDate": "2026-06-01",
  "returnDate": "2026-06-03",
  "pickupLocation": "Hanoi",
  "returnLocation": "Hanoi",
  "extras": [
    {
      "extraId": "uuid",
      "quantity": 1
    }
  ]
}
```

Response: `201 Created`
```json
{
  "id": "booking-uuid",
  "listingId": "listing-uuid",
  "pickupDate": "2026-06-01",
  "returnDate": "2026-06-03",
  "status": "HELD",
  "holdExpiresAt": "2026-05-09T11:15:00Z",
  "pickupLocation": "Hanoi",
  "returnLocation": "Hanoi",
  "priceSnapshot": {
    "baseAmount": 1400000,
    "extraAmount": 0,
    "totalAmount": 1400000,
    "currency": "VND",
    "rentalDays": 2,
    "extras": []
  },
  "policySnapshot": {
    "type": "FLEXIBLE",
    "cancellationPolicy": "..."
  },
  "createdAt": "2026-05-09T11:00:00Z"
}
```

### GET /api/v1/bookings/me

Query: `?status=&page=&size=`

### GET /api/v1/bookings/{id}

### PATCH /api/v1/bookings/{id}

Request:
```json
{
  "pickupLocation": "New pickup location",
  "returnLocation": "New return location"
}
```

Only pickupLocation and returnLocation can be updated.

### POST /api/v1/bookings/{id}/cancel

Headers: `Idempotency-Key: uuid`

Request:
```json
{
  "reason": "Change of plan"
}
```

## TX-01: Create Booking Transaction

```
@Transactional
createBooking() {
  1. Validate Idempotency-Key header format (UUID-v4)
  2. Resolve idempotency: insert/check record FOR UPDATE
  3. If COMPLETED + same hash -> return stored response
  4. If COMPLETED + different hash -> 409 IDEMPOTENCY_KEY_CONFLICT
  5. Validate: customer exists, driverVerificationStatus = APPROVED (if flag enabled)
  6. Validate: listing exists, listing.status = ACTIVE, vehicle.status = ACTIVE
  7. Validate: host_id != customer_id (BR-13)
  8. Validate: pickupDate < returnDate, rentalDays 1-30 (BR-09, BR-10)
  9. Validate: customer has no overlapping active booking (BR-11, BR-12)
  10. SELECT FOR UPDATE availability rows [pickupDate, returnDate) ORDER BY available_date ASC (BR-22, BR-24)
  11. Validate: all rows exist and status = FREE (BR-25)
  12. Generate holdToken = UUID
  13. Insert booking: status = HELD, holdExpiresAt = now + 15 min
  14. Update availability: status = HOLD, holdToken, holdExpiresAt, bookingId
  15. Do not create timeline/audit/outbox here. Timeline starts in Phase 7.
      Leave a comment hook for future extension.
  16. Save idempotency response as COMPLETED
  17. Commit
}
```

## Idempotency Behavior

| State | Same request hash | Different request hash |
|---|---|---|
| PROCESSING + lock active | 409 REQUEST_ALREADY_PROCESSING | 409 REQUEST_ALREADY_PROCESSING |
| PROCESSING + lock expired | retry allowed | retry allowed |
| COMPLETED | return stored response | 409 IDEMPOTENCY_KEY_CONFLICT |
| FAILED | retry allowed if lock expired | retry allowed if lock expired |

## Acceptance Criteria

- [ ] 10 concurrent booking requests for same listing/date -> exactly 1 success
- [ ] Same idempotency key + same body -> same response (idempotent)
- [ ] Same idempotency key + different body -> 409 IDEMPOTENCY_KEY_CONFLICT
- [ ] Invalid idempotency key format -> 400 VALIDATION_ERROR
- [ ] Customer with overlapping active booking -> 409 BOOKING_OVERLAP_CUSTOMER
- [ ] Customer booking own listing -> 409 ACCESS_DENIED
- [ ] Booking for non-ACTIVE listing -> 409 LISTING_NOT_FOUND
- [ ] Booking with invalid date range -> 400 VALIDATION_ERROR
- [ ] Expired HELD hold releases availability to FREE
- [ ] Expiry job uses bounded batch and FOR UPDATE SKIP LOCKED
- [ ] Two expiry job instances don't process same booking
- [ ] Booking patch only allows location fields
- [ ] Customer can view own bookings
- [ ] Host can view bookings for own listings
- [ ] Customer cannot view another customer's booking

## Tests Required

- [ ] Unit: Price calculation base + extras
- [ ] Unit: Canonical JSON hash stability
- [ ] Unit: Overlap detection with active statuses
- [ ] Unit: Date range validation (1-30 days)
- [ ] Integration: Create booking success -> HELD
- [ ] Integration: Create booking -> 365 days not needed (availability already exists)
- [ ] Integration: Same idempotency key -> same response
- [ ] Integration: Same key + different body -> 409
- [ ] Integration: Overlapping booking -> 409
- [ ] Integration: Self-booking -> 409
- [ ] **Integration: 10 concurrent requests -> exactly 1 success, 9 LISTING_NOT_AVAILABLE** (CRITICAL)
- [ ] Integration: Expired hold -> booking EXPIRED, availability FREE
- [ ] Integration: Expiry job batch processing
- [ ] Integration: Two expiry jobs -> no duplicate processing
- [ ] Integration: Booking patch location allowed
- [ ] Integration: Booking patch dates rejected
- [ ] Security: Customer cannot view another customer's booking
- [ ] Security: Unauthenticated booking -> 401

## Notes

- This is the **portfolio proof point**. The concurrent booking test proves your backend skills.
- Lock order must always be `available_date ASC` to prevent deadlocks.
- Use `@Lock(LockModeType.PESSIMISTIC_WRITE)` with JPQL or native query.
- The expiry job must use `FOR UPDATE SKIP LOCKED` so multiple instances don't conflict.
- Idempotency key table: TTL locked_until = 30 seconds, expires_at = 5 days.
- Feature flag: `rentflow.booking.require-driver-verification=false` disables driver verification gate in P0.
- Booking cancellation in this phase is basic: just HELD -> CANCELLED -> availability FREE.
