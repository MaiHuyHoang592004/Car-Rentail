# Testing Strategy — RentFlow

> Extract from SRS section 27.

## Test Pyramid

```
         /\
        /  \   E2E (manual demo)
       /----\
      /      \   Integration (Testcontainers)
     /--------\
    /          \  Unit Tests (validators, calculators, state machines)
   /____________\
```

**Rule: Test real DB behavior. Key flows use PostgreSQL Testcontainers. Never mock the database in integration tests.**

---

## Unit Tests

| Test | Purpose |
|---|---|
| PriceCalculator_calculatesBaseAndExtras | Validate deterministic pricing |
| CancellationPolicy_flexible_fullRefund | Validate refund policy |
| CancellationPolicy_moderate_partialRefund | Validate partial refund |
| CancellationPolicy_strict_noRefund | Validate no-refund case |
| CancellationFlow_partialPenalty_ordering_success | CAPTURE then VOID ordering |
| CancellationFlow_voidFailsAfterCapture_createsRetry | VOID failure recovery |
| BookingValidator_rejectsInvalidDateRange | Validate date rules |
| BookingValidator_rejectsSelfBooking | Host cannot book own car |
| BookingOverlap_activeStatusesOnly | HELD/PENDING/CONFIRMED are active |
| IdempotencyService_sameKeyDifferentBody_conflict | Idempotency conflict |
| Idempotency_hashCanonicalJson_stable | Hash algorithm stability |
| ListingStateMachine_rejectsInvalidTransition | Listing lifecycle |
| ListingSubmit_fromNonDraft_fails | Prevent double-submit |
| DriverVerification_expiredLicense_blocksNewBooking | Expired license gate |
| DriverVerification_duplicatePending_returns409 | Prevent duplicate verification |
| PaymentCapture_partialCapture_respectsAuthorizedAmount | Partial capture |
| PaymentCapture_overCapture_rejected | Prevent over-capture |
| VehicleStateMachine_transitionsAreValid | Vehicle lifecycle |
| VehicleArchive_requiresArchivedListings | Archive precondition |
| VehicleArchive_rejectsActiveBooking | Cannot archive with active booking |
| VehicleSuspension_activeListingAutoSuspended | Vehicle/listing coupling |
| BookingPatch_onlyLocationFieldsAllowed | Prevent forbidden updates |
| VehicleCreation_defaultStatus_isActive | New vehicle default status |
| VehicleArchive_cascadesAllListings | Archive cascade to listings |
| CancellationReason_sanitizedAndLengthLimited | Prevent script/oversized reason |

---

## Integration Tests with Testcontainers

### PostgreSQL Testcontainers Setup

```java
@Testcontainers
class BaseIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
}
```

### Concurrency (Critical — Portfolio Proof)

| Test | Expected |
|---|---|
| concurrentBooking_sameListingSameDates_onlyOneSucceeds | Exactly 1 success, 9 conflict |
| createBooking_sameIdempotencyKey_returnsSameResponse | Same response |
| createBooking_sameKeyDifferentBody_returns409 | Conflict |
| expireHeldBooking_releasesAvailability | Booking EXPIRED, dates FREE |
| authorizePayment_locksAvailabilityAndConfirms | HELD -> CONFIRMED, HOLD -> BOOKED |
| authorizePayment_whenAvailabilityReleased_returns409 | Race protection |
| authorizePayment_instantBook_confirmsBooking | Booking CONFIRMED, payment AUTHORIZED |
| cancelConfirmedBooking_appliesPolicy | Payment action matches policy |
| cancelConfirmed_partialPenalty_captureThenVoid | Payment operations ordered |
| cancelConfirmed_voidFails_schedulesRetry | Recovery path created |
| hostCannotApproveOtherHostBooking | 403 |
| customerCannotReadOtherCustomerBooking | 403 or 404 |
| adminApproveListing_generatesAvailabilityRows | 365 rows generated |
| searchListings_excludesBlockedHeldBookedDates | Correct results |
| driverVerificationExpiryJob_updatesExpired | APPROVED/PENDING -> EXPIRED |
| expiredDriverCannotCreateBooking | 403 DRIVER_LICENSE_NOT_APPROVED |
| duplicateDriverVerification_returns409 | 409 ALREADY_SUBMITTED |
| partialCaptureThenVoidRemaining_success | Penalty capture + remaining void |
| vehicleArchive_withActiveListings_returns409 | Cannot archive |
| vehicleArchive_allListingsArchived_success | Vehicle archived |
| vehicleSuspend_activeListingSuspended | Listing auto-suspended |
| vehicleSuspend_confirmedBookingRemainsValid | Existing booking unchanged |
| hostAvailabilityView_showsAllStatuses | Host sees HOLD/BOOKED/BLOCKED |
| bookingPatch_datesRejected | Dates cannot be patched |
| bookingPatch_locationAllowed | Location update works |
| vehicleCreation_defaultActiveStatus | New vehicle is ACTIVE by default |
| vehicleArchive_allListingsArchived_success | Listings cascade to ARCHIVED |
| vehicleArchive_withActiveBookings_rejected | VEHICLE_ARCHIVE_NOT_ALLOWED |
| cancellationReason_htmlStripped | Sanitized cancellation reason |

---

## Concurrency Tests

| Test | Expected |
|---|---|
| 10 users book same listing/date | 1 success, 9 LISTING_NOT_AVAILABLE |
| Same user sends same idempotency key in 2 threads | One execution, same response |
| Cancel and expire same booking simultaneously | One terminal state only |
| Authorize and cancel same booking simultaneously | No inconsistent availability/payment |
| Authorize payment twice with same key | No duplicate payment effect |
| Capture and cancel same payment | No over-capture |
| Host blocks while booking creation is running | No inconsistent availability |
| Double submit listing | Only one submit succeeds |
| Expiry job two instances | No double processing through SKIP LOCKED |

---

## Security Tests

| Test | Expected |
|---|---|
| JWT tampering | 401 |
| Customer accesses another booking | 403/404 |
| Customer accesses another booking payment | 403/404 |
| Host modifies another host listing | 403 |
| Host views another host availability | 403 |
| Customer calls admin API | 403 |
| Invalid Idempotency-Key format | 400 |
| Invalid idempotency scope | rejected by app or DB |
| SQL-like search payload | Safe parameterized handling |
| Host cannot see customer license number | Sensitive fields hidden |
| Payment amount zero | DB/app rejects |

---

## DB Tests

| Test | Expected |
|---|---|
| Flyway migration applies cleanly | Pass |
| Negative payment amount | DB rejects |
| Duplicate active listing for same vehicle | DB rejects |
| Duplicate plate hash | DB rejects |
| Duplicate active driver verification | DB rejects |
| Review rating outside 1-5 | DB rejects |
| Invalid availability status | DB rejects |
| Invalid payment type/status | DB rejects |
| Invalid idempotency scope | DB rejects |
| Invalid auth user status | DB rejects |
| Invalid vehicle status | DB rejects |
| Invalid listing status | DB rejects |
| Duplicate listing photo cover | DB rejects |
| Duplicate listing photo sort order | DB rejects |
| File with zero size | DB rejects |
| Negative authorized amount | DB rejects |

---

## Test Naming Convention

Use: `methodName_context_expectedBehavior`

Examples:
- `createBooking_sameIdempotencyKey_returnsSameResponse`
- `concurrentBooking_10requests_only1Succeeds`
- `authorizePayment_instantBook_confirmsBooking`
- `cancelConfirmed_partialPenalty_captureThenVoid`

---

## Required Test for Every Feature

1. Happy path works
2. Validation failures return correct error codes
3. Unauthorized access returns 401/403
4. Concurrency edge cases for booking/payment

---

## Most Important Test (Portfolio Proof)

```java
@Test
void concurrentBooking_sameListingSameDates_onlyOneSucceeds() {
    // 10 threads concurrently book the same listing + date range
    // Expected: exactly 1 booking SUCCESS
    //           9 bookings return 409 LISTING_NOT_AVAILABLE
    // Use CountDownLatch + ExecutorService
    // Verify: exactly 1 row in bookings table with status CONFIRMED
}
```

This proves: database transaction skill, pessimistic locking, idempotency behavior, business rule correctness, API error handling, production-style backend thinking.
