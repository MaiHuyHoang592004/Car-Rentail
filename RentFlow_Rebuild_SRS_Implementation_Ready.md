# Software Requirements Specification (SRS)

# RentFlow — Car Rental Booking System Rebuild

**Backend REST API-first | Java Spring Boot Portfolio Edition | Final Implementation-ready SRS v5.1**

---

## 0. Document Metadata

| Field | Value |
|---|---|
| Document type | Software Requirements Specification |
| Project | RentFlow — Car Rental Booking System |
| Version | 5.1 |
| Target architecture | Modular monolith REST API |
| Primary stack | Java 21, Spring Boot, PostgreSQL, Redis, MinIO, Testcontainers |
| Optional extension | Kafka / outbox publisher |
| Database | PostgreSQL |
| API style | REST + JSON |
| Main goal | Production-style backend portfolio project |
| Document date | 2026-05-09 |

---

## 1. Purpose

RentFlow is a rebuilt backend-first car rental booking system designed for a strong Java Spring backend portfolio.

This SRS focuses on implementation-ready requirements while keeping scope realistic. It clarifies:

- API contracts
- business rules
- database constraints
- transaction boundaries
- booking/payment state machines
- idempotency behavior
- concurrency handling
- resource-level authorization
- scheduled job behavior
- Testcontainers strategy
- phased implementation roadmap

The primary portfolio proof point is:

```text
Idempotency-Key + PostgreSQL SELECT FOR UPDATE + Testcontainers concurrent booking test
```

---

## 2. Core Decisions

| ID | Decision | Rationale |
|---|---|---|
| D-01 | Use REST API-first architecture. | Easy to demo with Swagger/Postman and reusable by frontend/mobile clients. |
| D-02 | Use modular monolith. | Easier to build than microservices while still demonstrating clean boundaries. |
| D-03 | Use PostgreSQL. | Strong transactions, row locks, JSONB, indexes, Testcontainers support. |
| D-04 | MVP uses day-based rental. | Keeps booking and availability deterministic. |
| D-05 | Booking period is `[pickupDate, returnDate)`. | Clear date-range semantics. |
| D-06 | Use pessimistic locking for booking availability. | Best fit for double-booking prevention. |
| D-07 | Booking state and payment state are separate. | Avoids mixed lifecycle logic. |
| D-08 | Payment is authorized upfront and captured at checkout. | Supports rental final charges and cancellation penalties. |
| D-09 | Payment stub supports partial capture. | Required for cancellation penalty. |
| D-10 | Idempotency is required for critical state-changing operations. | Prevents duplicate booking/payment/cancel/approval effects. |
| D-11 | Driver verification is enforced in final portfolio behavior. | Makes customer eligibility meaningful. |
| D-12 | Driver verification expiry is automatic. | Prevents stale approved licenses. |
| D-13 | Vehicle delete is soft delete/archive. | Preserves booking history. |
| D-14 | Files are stored in MinIO/S3; database stores metadata only. | Avoids app-local/static upload folders. |
| D-15 | Kafka is optional. | Scheduled outbox publisher is sufficient before Kafka. |

---

## 3. Scope

## 3.1 P0 — Core Backend Portfolio

P0 is the minimum version that must be implemented first.

| Area | Scope |
|---|---|
| Auth | Register, login, refresh token, logout, RBAC |
| User | Basic profile |
| Vehicle | Host creates and manages own vehicles |
| Listing | Host creates listing, submits for approval; admin approves/rejects |
| Search | Public listing search with filters and pagination |
| Availability | Generate 365-day availability rows; block/unblock dates |
| Booking | Create booking hold with idempotency and pessimistic locking |
| Scheduler | Expire HELD bookings after 15 minutes using bounded batches |
| API docs | Swagger/OpenAPI |
| DB | PostgreSQL + Flyway |
| Tests | Testcontainers concurrency test: 10 requests, exactly 1 success |
| DevOps | Docker Compose for API + PostgreSQL + Redis |

## 3.2 P1 — Business Completeness

| Area | Scope |
|---|---|
| Payment Stub | Authorize, void, capture, partial capture, refund |
| Cancellation | Cancellation policy and refund/void/capture-penalty behavior |
| Driver Verification | Upload license, admin approve/reject, daily expiry job, booking gate |
| Audit | Audit log for important actions |
| Timeline | Booking timeline |
| Notification | Database notifications |
| Rate limiting | Login and booking attempt limits |
| Resource-level security | Strong ownership checks |
| Files metadata | `files` table and listing photo metadata |

## 3.3 P2 — Advanced Portfolio Extensions

| Area | Scope |
|---|---|
| MinIO full flow | Signed URLs, file permission rules |
| Trip lifecycle | Check-in, check-out, odometer, fuel, photos |
| Reviews | Completed-booking reviews |
| Disputes | Complaint/dispute handling |
| Reports | Revenue, host earnings, utilization |
| Payouts | Host payout calculation |
| Outbox | Domain events and scheduled publisher |
| Kafka | Optional event bus integration |
| CI | Automated tests in CI |
| Observability | Actuator metrics, structured logs |

---

## 4. System Context

```text
Client Apps / Swagger / Postman
        |
        v
Spring Boot REST API
        |
        |-- PostgreSQL
        |     users, roles, vehicles, listings, availability,
        |     bookings, payments, files, audit, outbox
        |
        |-- Redis
        |     refresh-token blacklist, rate limiting, optional cache
        |
        |-- MinIO / S3
        |     listing photos, driver license documents, trip photos
        |
        |-- Scheduler
        |     expire booking holds, expire driver verifications,
        |     generate availability, retry notifications, publish outbox events
        |
        |-- Kafka optional
              async event integration
```

---

## 5. Architecture

## 5.1 Target Architecture

The system is a **modular monolith**.

One Spring Boot application is deployed, but code is organized by business modules.

## 5.2 Suggested Package Structure

```text
com.rentflow
├── auth
├── user
├── vehicle
├── listing
├── availability
├── booking
├── payment
├── trip
├── review
├── dispute
├── notification
├── audit
├── outbox
├── report
└── common
    ├── config
    ├── exception
    ├── security
    ├── pagination
    ├── validation
    └── util
```

Each module may contain:

```text
controller
application/service
domain/entity
repository
dto
mapper
event
policy
```

## 5.3 Architecture Principles

| Principle | Requirement |
|---|---|
| API-first | All business capabilities are exposed through REST APIs. |
| Domain module ownership | Each module owns its own business logic and DTOs. |
| Transactional consistency | Booking, availability, payment, audit, and outbox updates must use clear transaction boundaries. |
| No mixed lifecycle state | Booking and payment statuses are separate. |
| Test real DB behavior | Key flows use PostgreSQL Testcontainers. |
| No app-local file storage | Files go to MinIO/S3; DB stores metadata. |
| Security by role and resource | Role checks are not enough; ownership checks are required. |

---

## 6. Actors and Roles

| Actor | Description | Main Permissions |
|---|---|---|
| Guest | Unauthenticated user | Search active listings, view listing details, register, login |
| Customer | Registered renter | Manage profile, create bookings, pay, cancel, view own bookings |
| Host | Vehicle owner | Manage own vehicles/listings, manage availability, handle own booking requests |
| Admin | System operator | Manage users, approve listings, verify licenses, view all bookings, audit logs |
| Scheduler/Worker | Background actor | Expire holds, expire licenses, generate availability, publish events |
| Payment Provider Stub | Internal sandbox service | Authorize, capture, void, refund |

Roles:

```text
CUSTOMER
HOST
ADMIN
```

A user may have multiple roles through `user_roles`.

---

## 7. Rental Model

## 7.1 Date-Based Rental

MVP uses day-based rental.

```text
pickupDate: DATE
returnDate: DATE
```

Booking period:

```text
[pickupDate, returnDate)
```

Example:

```text
pickupDate = 2026-06-01
returnDate = 2026-06-03
```

Occupied dates:

```text
2026-06-01
2026-06-02
```

Not occupied:

```text
2026-06-03
```

## 7.2 Rental Duration

```text
minimum rental duration = 1 day
maximum rental duration = 30 days
```

The system must reject:

```text
returnDate <= pickupDate
returnDate - pickupDate > 30 days
```

---

## 8. Vehicle Lifecycle

## 8.1 Vehicle Statuses

```text
DRAFT
ACTIVE
MAINTENANCE
SUSPENDED
ARCHIVED
```

## 8.2 Allowed Transitions

```text
DRAFT -> ACTIVE
ACTIVE -> MAINTENANCE
ACTIVE -> SUSPENDED
ACTIVE -> ARCHIVED, only if archive preconditions pass
MAINTENANCE -> ACTIVE
MAINTENANCE -> SUSPENDED
SUSPENDED -> ACTIVE
SUSPENDED -> MAINTENANCE
Any -> ARCHIVED, only if archive preconditions pass
```

## 8.3 Vehicle Rules

| Rule | Description |
|---|---|
| Soft delete | `DELETE /api/v1/host/vehicles/{id}` sets vehicle status to ARCHIVED. |
| No hard delete | Vehicle rows are not physically deleted in normal API flow. |
| Archive precondition | A vehicle can be archived only when all its listings are ARCHIVED and no HELD/CONFIRMED/IN_PROGRESS bookings exist. |
| Status coupling | If vehicle becomes MAINTENANCE or SUSPENDED, ACTIVE listings for that vehicle become SUSPENDED. |
| Archive cascade | If vehicle becomes ARCHIVED, all non-ARCHIVED listings become ARCHIVED only if archive preconditions pass. |
| Existing bookings | CONFIRMED/IN_PROGRESS bookings remain valid when vehicle/listing is suspended unless admin cancels for safety/fraud. |
| New bookings | No new bookings can be created for listings whose vehicle is not ACTIVE. |
| Default status | New vehicles are created with status ACTIVE by default. |
| Draft option | Host may optionally create a vehicle in DRAFT status to save progress before finalizing. |
| DRAFT restriction | DRAFT vehicles cannot be linked to ACTIVE listings. |
| Check-in precondition | Check-in requires booking CONFIRMED, listing ACTIVE, and vehicle ACTIVE. |

---

## 9. Availability Model

## 9.1 Availability Statuses

```text
FREE
HOLD
BOOKED
BLOCKED
```

| Status | Meaning |
|---|---|
| FREE | Date can be booked |
| HOLD | Date is temporarily held for a booking |
| BOOKED | Date is confirmed/booked |
| BLOCKED | Host/admin manually blocked the date |

## 9.2 Availability Rules

| Rule | Description |
|---|---|
| One row per listing/date | Primary key is `(listing_id, available_date)`. |
| Rows generated in advance | When a listing becomes ACTIVE, generate 365 rows. |
| Missing row means unavailable | Booking/search should not treat missing rows as FREE. |
| Create booking locks rows | Lock all requested date rows in one transaction. |
| Authorize payment locks rows again | Before HOLD → BOOKED, TX-02 locks availability rows. |
| Lock order is deterministic | Always lock by `available_date ASC`. |
| Hold expires | HELD booking expires after 15 minutes. |
| Host block conflict | Host cannot block dates that are HOLD or BOOKED. |

## 9.3 Booking-to-Availability Mapping

| Booking Status | Availability Status |
|---|---|
| HELD | HOLD |
| PENDING_HOST_APPROVAL | HOLD |
| CONFIRMED | BOOKED |
| IN_PROGRESS | BOOKED |
| COMPLETED | BOOKED |
| CANCELLED before check-in | FREE |
| REJECTED | FREE |
| EXPIRED | FREE |

---

## 10. Listing Lifecycle

## 10.1 Listing Statuses

```text
DRAFT
PENDING_APPROVAL
ACTIVE
SUSPENDED
ARCHIVED
```

## 10.2 Allowed Transitions

```text
DRAFT -> PENDING_APPROVAL
PENDING_APPROVAL -> ACTIVE
PENDING_APPROVAL -> DRAFT
ACTIVE -> SUSPENDED
SUSPENDED -> ACTIVE
DRAFT/PENDING_APPROVAL/ACTIVE/SUSPENDED -> ARCHIVED
```

## 10.3 Listing Rules

| Rule | Description |
|---|---|
| Admin approval required | A listing must be approved by admin before becoming ACTIVE. |
| One active listing per vehicle | A vehicle may have at most one ACTIVE listing. |
| Vehicle status required | Vehicle must be ACTIVE before listing can become ACTIVE. |
| Vehicle status coupling | If vehicle becomes MAINTENANCE, SUSPENDED, or ARCHIVED, listing state is adjusted. |
| Existing bookings survive suspension | CONFIRMED/IN_PROGRESS bookings remain valid when listing is suspended. |
| Archive restriction | Listing cannot be archived if it has HELD, CONFIRMED, or IN_PROGRESS bookings. |
| Availability generation | Approval to ACTIVE generates 365 availability rows synchronously in the same transaction. |
| Duplicate submit | Submit is valid only from DRAFT → PENDING_APPROVAL. Double submit returns conflict. |
| Submit preconditions | Listing status must be DRAFT and vehicle status must be ACTIVE. |
| Photos optional at submit | At least one listing photo is recommended but not required for P0/P1. Photos may be added after approval in P2. |

---

## 11. Booking State Machine

## 11.1 Booking Statuses

```text
HELD
PENDING_HOST_APPROVAL
CONFIRMED
IN_PROGRESS
COMPLETED
CANCELLED
REJECTED
EXPIRED
```

| Status | Meaning | Allowed Next Statuses |
|---|---|---|
| HELD | Availability is temporarily held. Payment authorization may happen during hold window. | CONFIRMED, PENDING_HOST_APPROVAL, CANCELLED, EXPIRED |
| PENDING_HOST_APPROVAL | Payment is authorized; host approval required. P1 scope. | CONFIRMED, REJECTED, CANCELLED, EXPIRED |
| CONFIRMED | Booking is accepted and availability is booked. | IN_PROGRESS, CANCELLED |
| IN_PROGRESS | Customer has picked up the car. | COMPLETED |
| COMPLETED | Trip completed and payment captured. | None |
| CANCELLED | Booking cancelled. | None |
| REJECTED | Host rejected booking. | None |
| EXPIRED | Hold or approval window expired. | None |

## 11.2 Active Booking Definition

For customer overlap detection, active statuses are:

```text
HELD
PENDING_HOST_APPROVAL
CONFIRMED
```

Not active for overlap detection:

```text
COMPLETED
CANCELLED
REJECTED
EXPIRED
```

`HELD` bookings count as active until they expire or are cancelled.

## 11.3 Instant Booking Flow

P0 supports instant booking.

```text
1. Customer calls POST /api/v1/bookings.
2. System creates booking with status HELD.
3. Availability rows become HOLD.
4. Customer calls POST /api/v1/bookings/{id}/payments/authorize.
5. System locks booking, payment, and availability rows.
6. Payment authorization succeeds.
7. Since listing.instantBook=true:
   - booking HELD -> CONFIRMED
   - availability HOLD -> BOOKED
   - booking_payment UNPAID -> AUTHORIZED
```

## 11.4 Manual Host Approval Flow

P1 scope.

```text
1. Customer creates booking HELD.
2. Customer authorizes payment.
3. Since listing.instantBook=false:
   - booking HELD -> PENDING_HOST_APPROVAL
   - host_approval_expires_at = now + 24 hours
   - availability remains HOLD
4. Host approves:
   - booking -> CONFIRMED
   - availability -> BOOKED
5. Host rejects:
   - booking -> REJECTED
   - payment authorization -> VOIDED
   - availability -> FREE
6. Host does not respond:
   - booking -> EXPIRED
   - payment authorization -> VOIDED
   - availability -> FREE
```

## 11.5 Host Visibility for Pending Approval

For `PENDING_HOST_APPROVAL` bookings, host may see:

```text
listing summary
vehicle summary
booking dates
pickup location
customer full name
driver verification status only
price snapshot
```

Host must not see:

```text
customer address
customer phone
license number
license document
payment details
token/payment provider references
```

---

## 12. Payment State Machine

## 12.1 Booking Payment Aggregate

Table: `booking_payments`

Statuses:

```text
UNPAID
AUTHORIZED
CAPTURED
PARTIALLY_REFUNDED
REFUNDED
VOIDED
FAILED
```

| Status | Meaning |
|---|---|
| UNPAID | No successful payment authorization/capture exists |
| AUTHORIZED | Amount reserved but not captured |
| CAPTURED | Amount captured |
| PARTIALLY_REFUNDED | Some captured amount refunded |
| REFUNDED | Full captured amount refunded |
| VOIDED | Authorization released before capture |
| FAILED | Latest payment attempt failed and no valid authorization/capture exists |

## 12.2 Payment Transactions

Table: `payment_transactions`

Types:

```text
AUTHORIZE
CAPTURE
VOID
REFUND
```

Statuses:

```text
PENDING
SUCCEEDED
FAILED
```

## 12.3 Payment Timing

| Event | Payment behavior |
|---|---|
| Booking creation | No payment yet; booking is HELD. |
| Payment authorization | Reserves amount; does not capture money. |
| Instant booking after authorization | Booking becomes CONFIRMED; payment remains AUTHORIZED. |
| Manual booking after authorization | Booking becomes PENDING_HOST_APPROVAL; payment remains AUTHORIZED. |
| Host rejection / approval timeout | Authorization is VOIDED. |
| Check-out | Payment is CAPTURED. |
| Cancellation before capture | VOID or partial CAPTURE + VOID remaining depending on policy. |
| Cancellation after capture | REFUND depending on policy/admin decision. |

## 12.4 Partial Capture Rule

The payment stub must support partial capture.

```text
captureAmount <= authorizedAmount - capturedAmount
```

For cancellation penalty:

```text
1. Capture penalty amount.
2. Void remaining authorized amount.
3. Update booking_payments aggregate.
```

Example:

```text
authorized_amount = 1,000,000
penalty = 300,000

CAPTURE 300,000
VOID 700,000
```

---

## 13. Price Calculation

## 13.1 Rental Days

```text
rentalDays = returnDate - pickupDate
```

## 13.2 Base Price

```text
baseAmount = listing.basePricePerDay × rentalDays
```

## 13.3 Extras

```text
extraAmount =
  SUM(
    if pricingType = PER_DAY:
      extra.price × quantity × rentalDays
    if pricingType = PER_TRIP:
      extra.price × quantity
  )
```

## 13.4 Total Amount

P0/P1:

```text
totalAmount = baseAmount + extraAmount
tax = 0
platformFee = 0
deposit = 0
```

P2 may add:

```text
tax
platform fee
deposit
late fee
mileage overage
```

Price and policy snapshots must be stored at booking creation time.

---

## 14. Cancellation Policy

## 14.1 Cancellation Context

```text
pickupDateStart = pickupDate at 00:00 in system timezone
now = current timestamp
hoursBeforePickup = duration between now and pickupDateStart
refundPercent = lookup(policy, hoursBeforePickup)
```

## 14.2 FLEXIBLE

| Condition | Refund |
|---|---|
| Cancel at least 24 hours before pickup | 100% |
| Cancel less than 24 hours before pickup | 80% |
| Cancel after check-in | No automatic cancellation; dispute required |

## 14.3 MODERATE

| Condition | Refund |
|---|---|
| Cancel at least 72 hours before pickup | 100% |
| Cancel 24–72 hours before pickup | 50% |
| Cancel less than 24 hours before pickup | 0% |
| Cancel after check-in | No automatic cancellation; dispute required |

## 14.4 STRICT

| Condition | Refund |
|---|---|
| Cancel at least 7 days before pickup | 100% |
| Cancel less than 7 days before pickup | 0% |
| Cancel after check-in | No automatic cancellation; dispute required |

## 14.5 Cancellation Actions

| Booking/payment state | System behavior |
|---|---|
| HELD, no payment | CANCELLED, availability FREE |
| PENDING_HOST_APPROVAL, AUTHORIZED | CANCELLED, VOID authorization, availability FREE |
| CONFIRMED, AUTHORIZED, refund 100% | CANCELLED, VOID authorization, availability FREE |
| CONFIRMED, AUTHORIZED, refund < 100% | CAPTURE penalty amount, VOID remaining authorization, CANCELLED, availability FREE |
| IN_PROGRESS | Reject customer cancellation; suggest dispute |
| COMPLETED | Reject normal cancellation; refund only through dispute/admin flow |

## 14.6 Transaction Ordering for Full Refund Before Capture

For `CONFIRMED`, `AUTHORIZED`, `refundPercent = 100%`:

```text
1. VOID full authorized_amount.
2. booking_payments:
   - authorized_amount = 0
   - status = VOIDED
3. booking -> CANCELLED
4. availability BOOKED -> FREE
5. timeline/audit/outbox
```

If VOID fails:

```text
rollback booking state
create failed VOID transaction
schedule retry
alert admin/operator
return PAYMENT_FAILED or warning depending on configured mode
```

## 14.7 Transaction Ordering for Partial Penalty Before Capture

For `CONFIRMED`, `AUTHORIZED`, `refundPercent < 100%`:

```text
1. Calculate penaltyAmount = totalAmount × (1 - refundPercent).
2. CAPTURE penaltyAmount.
3. VOID remainingAuthorizedAmount = authorizedAmount - penaltyAmount.
4. Update booking_payments:
   - captured_amount += penaltyAmount
   - authorized_amount = 0
   - status = CAPTURED or PARTIALLY_REFUNDED depending on final settlement model
5. booking -> CANCELLED
6. availability BOOKED -> FREE
7. create timeline/audit/outbox entries
```

Failure handling:

```text
If CAPTURE fails:
    rollback all booking/payment/availability changes
    return PAYMENT_FAILED

If VOID fails after CAPTURE succeeds:
    record failed VOID payment_transaction
    create retry task/outbox event
    keep cancellation intent recorded
    alert admin/operator
    return success with warning:
        cancellationCompleted = true
        voidRetryRequired = true
```

---

## 15. Idempotency

## 15.1 Required Operations

Idempotency is required for:

```text
CREATE_BOOKING
AUTHORIZE_PAYMENT
CAPTURE_PAYMENT
VOID_PAYMENT
REFUND_PAYMENT
CANCEL_BOOKING
HOST_APPROVE_BOOKING
HOST_REJECT_BOOKING
```

## 15.2 Header Format

```http
Idempotency-Key: UUID-v4
```

Invalid format returns:

```text
400 VALIDATION_ERROR
```

## 15.3 Request Hash

```text
request_hash = SHA-256(canonicalJson(requestBody))
```

Canonical JSON rules:

```text
object keys sorted alphabetically
UTF-8 encoded
no insignificant whitespace
null values preserved
arrays keep original order
```

## 15.4 Idempotency Record

Key:

```text
(user_id, scope, key)
```

TTL:

```text
locked_until = now + 30 seconds
expires_at = now + 5 days
```

## 15.5 Behavior

| State | Same request hash | Different request hash |
|---|---|---|
| PROCESSING and lock active | 409 REQUEST_ALREADY_PROCESSING | 409 REQUEST_ALREADY_PROCESSING |
| PROCESSING and lock expired | retry allowed | retry allowed after row lock |
| COMPLETED | return stored response | 409 IDEMPOTENCY_KEY_CONFLICT |
| FAILED | retry allowed if lock expired | retry allowed if lock expired |

Important note:

```text
FAILED means the idempotency workflow failed, but the business operation may have already committed.
Retry logic must first check whether the original business result already exists before creating a new record.
For CREATE_BOOKING, retry must check for an existing active booking with the same customer, listing, and date range.
```

---

## 16. Driver Verification

## 16.1 Statuses

```text
NOT_SUBMITTED
PENDING
APPROVED
REJECTED
EXPIRED
```

## 16.2 Rules

| Rule | Description |
|---|---|
| Booking gate | Final portfolio behavior requires APPROVED driver verification before new booking creation. |
| Dev feature flag | P0 may disable gate through `rentflow.booking.require-driver-verification=false`. |
| Expiry | APPROVED/PENDING verification expires when `license_expiry_date < current_date`. |
| Expiry job | Daily job runs at 00:00 UTC. |
| Existing bookings | Expiry does not cancel existing bookings. |
| Duplicate submission | A customer may have at most one PENDING or APPROVED verification. |
| Re-submission | Allowed only after REJECTED or EXPIRED. |
| Sensitive data | Host never sees license number or license document. |

---

## 17. Business Rules

| ID | Rule |
|---|---|
| BR-01 | Only ACTIVE listings are visible in public search. |
| BR-02 | Customer books by listingId, not vehicleId. |
| BR-03 | Host may manage only own vehicles/listings. |
| BR-04 | A listing must be admin-approved before becoming ACTIVE. |
| BR-05 | Vehicle must be ACTIVE before its listing can become ACTIVE. |
| BR-06 | A vehicle may have at most one ACTIVE listing. |
| BR-07 | Approval to ACTIVE generates 365 availability rows synchronously. |
| BR-08 | Missing availability row means unavailable. |
| BR-09 | Booking period is `[pickupDate, returnDate)`. |
| BR-10 | Minimum rental is 1 day; maximum is 30 days. |
| BR-11 | Customer cannot have overlapping active bookings. |
| BR-12 | Active booking statuses for overlap are HELD, PENDING_HOST_APPROVAL, CONFIRMED. |
| BR-13 | Customer cannot book own hosted listing. |
| BR-14 | Final portfolio behavior requires APPROVED driver verification before booking. |
| BR-15 | Driver verification gate may be disabled only through explicit local/dev feature flag. |
| BR-16 | Driver verification automatically expires when license_expiry_date < current_date. |
| BR-17 | Expired driver verification blocks new booking creation but does not cancel existing bookings. |
| BR-18 | A customer may have at most one PENDING or APPROVED driver verification. |
| BR-19 | Create booking requires Idempotency-Key. |
| BR-20 | Same idempotency key + same body returns same response. |
| BR-21 | Same idempotency key + different body returns conflict. |
| BR-22 | Booking creation locks availability rows using SELECT FOR UPDATE. |
| BR-23 | Authorize payment locks availability rows before HOLD → BOOKED. |
| BR-24 | Availability rows are locked in ascending available_date order. |
| BR-25 | If any requested date is not FREE during booking creation, return LISTING_NOT_AVAILABLE. |
| BR-26 | HELD booking expires after 15 minutes. |
| BR-27 | PENDING_HOST_APPROVAL booking expires after 24 hours. |
| BR-28 | Payment authorization happens before booking confirmation. |
| BR-29 | Payment capture happens at check-out. |
| BR-30 | Payment stub supports partial capture. |
| BR-31 | Booking and payment states are separate. |
| BR-32 | Host rejection/approval expiry voids authorization and releases availability. |
| BR-33 | Cancellation must be idempotent. |
| BR-34 | Customer cancellation is not allowed after check-in. |
| BR-35 | Vehicle deletion is soft delete; it sets vehicle status to ARCHIVED. |
| BR-36 | A vehicle can be archived only when all related listings are ARCHIVED and no HELD, CONFIRMED, or IN_PROGRESS bookings exist. |
| BR-37 | If vehicle becomes MAINTENANCE or SUSPENDED, ACTIVE listings become SUSPENDED. |
| BR-38 | If vehicle becomes ARCHIVED, all non-ARCHIVED listings become ARCHIVED only if archive preconditions are satisfied. |
| BR-39 | Existing CONFIRMED/IN_PROGRESS bookings remain valid when vehicle/listing is suspended unless admin cancels for safety/fraud. |
| BR-40 | Check-in requires booking CONFIRMED, listing ACTIVE, and vehicle ACTIVE. |
| BR-41 | Host cannot access customer license, license document, payment info, address, or phone in pending approval view. |
| BR-42 | Admin destructive actions require reason and audit log. |
| BR-43 | Audit logs must not store password hash, token hash, encrypted PII, or payment-sensitive data. |
| BR-44 | File buckets are private by default. |
| BR-45 | Outbox events must be created in the same transaction as business state change. |
| BR-46 | Listing submit is valid only from DRAFT to PENDING_APPROVAL. |
| BR-47 | Cancellation with partial penalty must execute CAPTURE before VOID remaining authorization. |
| BR-48 | If CAPTURE succeeds but VOID fails, the system must record failed VOID transaction, schedule retry, and alert admin/operator. |
| BR-49 | Expiry jobs must use bounded batches and FOR UPDATE SKIP LOCKED. |
| BR-50 | Booking patch, if implemented, may update only pickupLocation and returnLocation. |
| BR-51 | New vehicles are created with status ACTIVE by default unless host explicitly saves as DRAFT. |
| BR-52 | DRAFT vehicles cannot be linked to ACTIVE listings. |
| BR-53 | Listing submit requires listing status DRAFT and vehicle status ACTIVE. |
| BR-54 | Listing photos are recommended but not required for P0/P1 submission. |
| BR-55 | cancellation_reason must be sanitized and limited to 500 characters. |
| BR-56 | Vehicle archive must archive all non-ARCHIVED listings before archiving the vehicle, in the same transaction. |

---

## 18. Functional Requirements

## 18.1 Authentication and Authorization

| ID | Requirement | Priority |
|---|---|---|
| FR-AUTH-01 | System shall provide register/login/refresh/logout APIs. | P0 |
| FR-AUTH-02 | Passwords shall be hashed using BCrypt or stronger. | P0 |
| FR-AUTH-03 | Access token shall be short-lived JWT. | P0 |
| FR-AUTH-04 | Refresh token shall be stored as hash and rotated. | P0 |
| FR-AUTH-05 | Role-based access shall use CUSTOMER, HOST, ADMIN. | P0 |
| FR-AUTH-06 | Protected APIs shall return JSON 401/403, never HTML redirect. | P0 |
| FR-AUTH-07 | Login rate limiting shall be implemented using Redis. | P1 |
| FR-AUTH-08 | ADMIN role assignment is not exposed through public API in P0/P1. | P0 |

## 18.2 User and Driver Verification

| ID | Requirement | Priority |
|---|---|---|
| FR-USER-01 | Authenticated users can view/update own profile. | P0 |
| FR-USER-02 | Customer can submit driver license verification. | P1 |
| FR-USER-03 | Admin can approve/reject driver verification with reason. | P1 |
| FR-USER-04 | Final booking behavior requires APPROVED driver verification. | P1 |
| FR-USER-05 | License number must be stored encrypted and hashed. | P1 |
| FR-USER-06 | Driver verification status shall auto-expire when license_expiry_date < current_date. | P1 |
| FR-USER-07 | A daily scheduled job at 00:00 UTC shall update expired driver verifications. | P1 |
| FR-USER-08 | Customer may not create new booking when status is EXPIRED, REJECTED, or NOT_SUBMITTED. | P1 |
| FR-USER-09 | Existing HELD/CONFIRMED bookings remain valid after license expiry. | P1 |
| FR-USER-10 | Duplicate PENDING/APPROVED verification submissions return 409 ALREADY_SUBMITTED. | P1 |

## 18.3 Vehicle and Listing

| ID | Requirement | Priority |
|---|---|---|
| FR-LIST-01 | Host can create/update/archive own vehicles. | P0 |
| FR-LIST-02 | Host can create listing for own vehicle. | P0 |
| FR-LIST-03 | Host can submit listing for admin approval. | P0 |
| FR-LIST-04 | Admin can approve/reject/suspend/reactivate listing. | P0 |
| FR-LIST-05 | Public search returns only ACTIVE listings. | P0 |
| FR-LIST-06 | A vehicle can have at most one ACTIVE listing. | P0 |
| FR-LIST-07 | Host cannot archive listing with active bookings. | P0 |
| FR-LIST-08 | Host cannot archive/delete vehicle with non-archived listings or active bookings. | P0 |
| FR-LIST-09 | Suspending vehicle automatically suspends active listing. | P1 |
| FR-LIST-10 | Vehicle status transitions must follow the vehicle state machine. | P0 |

## 18.4 Search and Availability

| ID | Requirement | Priority |
|---|---|---|
| FR-AVL-01 | Listing search supports city, category, dates, price, seats, transmission, fuel type, page, size. | P0 |
| FR-AVL-02 | Search excludes BLOCKED, HOLD, BOOKED dates. | P0 |
| FR-AVL-03 | System generates 365 availability rows when listing becomes ACTIVE. | P0 |
| FR-AVL-04 | Host can block/unblock FREE dates. | P0 |
| FR-AVL-05 | Booking creation locks requested rows using SELECT FOR UPDATE. | P0 |
| FR-AVL-06 | Payment authorization locks HOLD rows before updating them to BOOKED. | P1 |
| FR-AVL-07 | HELD booking expiration releases HOLD rows. | P0 |
| FR-AVL-08 | Host can view full availability calendar for own listing. | P1 |
| FR-AVL-09 | Guest/customer availability view hides sensitive HOLD booking details. | P1 |
| FR-AVL-10 | `ratingAverage` in search response is null/omitted in P0/P1. | P0 |

## 18.5 Booking

| ID | Requirement | Priority |
|---|---|---|
| FR-BOOK-01 | Customer can create booking with listingId, pickupDate, returnDate, locations, extras. | P0 |
| FR-BOOK-02 | Create booking requires Idempotency-Key. | P0 |
| FR-BOOK-03 | System validates date range, listing active, vehicle active, availability, customer overlap. | P0 |
| FR-BOOK-04 | System prevents self-booking. | P0 |
| FR-BOOK-05 | Booking creation returns HELD status. | P0 |
| FR-BOOK-06 | System stores price/policy snapshots. | P0 |
| FR-BOOK-07 | System expires HELD bookings after 15 minutes. | P0 |
| FR-BOOK-08 | Customer/host/admin can cancel only according to status and role rules. | P1 |
| FR-BOOK-09 | Host approval flow is P1. | P1 |
| FR-BOOK-10 | Extend booking is P2. | P2 |
| FR-BOOK-11 | Booking patch, if implemented, may update only pickup/return location. | P1/P2 |

## 18.6 Payment

| ID | Requirement | Priority |
|---|---|---|
| FR-PAY-01 | System provides payment stub for authorize/capture/void/refund. | P1 |
| FR-PAY-02 | Payment authorization confirms instant booking. | P1 |
| FR-PAY-03 | Payment capture happens at checkout. | P2 |
| FR-PAY-04 | Every payment attempt creates a transaction record. | P1 |
| FR-PAY-05 | Payment mutation APIs require Idempotency-Key. | P1 |
| FR-PAY-06 | Cancellation uses void/refund/capture penalty logic. | P1 |
| FR-PAY-07 | Payment stub supports partial capture. | P1 |
| FR-PAY-08 | Payment state transitions lock booking_payments row. | P1 |
| FR-PAY-09 | Cancellation partial penalty must define CAPTURE then VOID ordering. | P1 |
| FR-PAY-10 | VOID failure after successful CAPTURE must create retry task/outbox event and alert admin/operator. | P1 |

## 18.7 Audit, Notification, Outbox

| ID | Requirement | Priority |
|---|---|---|
| FR-AUD-01 | Important actions create audit logs. | P1 |
| FR-AUD-02 | Booking status changes create timeline entries. | P1 |
| FR-NOTI-01 | System creates DB notifications for key events. | P1 |
| FR-NOTI-02 | Driver verification expiry creates customer notification. | P1 |
| FR-OUTBOX-01 | System creates outbox events in same transaction as business state changes. | P2 |
| FR-OUTBOX-02 | Scheduler publishes/retries outbox events. | P2 |

---

## 19. API Catalog

## 19.1 Auth

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

## 19.2 User Profile

```http
GET /api/v1/users/me
PATCH /api/v1/users/me
```

Response:

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "roles": ["CUSTOMER"],
  "fullName": "Nguyen Van A",
  "phone": "0900000000",
  "dateOfBirth": "1999-01-01",
  "addressLine": "Hanoi",
  "driverVerificationStatus": "APPROVED"
}
```

## 19.3 Driver Verification

```http
POST /api/v1/users/me/driver-license
GET  /api/v1/admin/driver-verifications?status=&page=&size=
POST /api/v1/admin/driver-verifications/{id}/approve
POST /api/v1/admin/driver-verifications/{id}/reject
```

Duplicate submission:

```text
409 ALREADY_SUBMITTED
```

## 19.4 Listing Search

```http
GET /api/v1/listings
```

Query params:

| Param | Type | Required | Notes |
|---|---|---|---|
| city | string | No | exact or prefix match |
| category | string[] | No | SEDAN, SUV, HATCHBACK, MPV, PICKUP, LUXURY, VAN |
| pickupDate | date | No | required with returnDate |
| returnDate | date | No | must be after pickupDate |
| minPrice | decimal | No | >= 0 |
| maxPrice | decimal | No | >= minPrice |
| seats | int | No | minimum seats |
| transmission | string | No | AUTO, MANUAL |
| fuelType | string | No | GASOLINE, DIESEL, EV, HYBRID |
| page | int | No | default 0 |
| size | int | No | default 20, max 100 |

Response:

```json
{
  "content": [
    {
      "id": "listing-id",
      "title": "Toyota Vios 2022",
      "city": "Hanoi",
      "category": "SEDAN",
      "basePricePerDay": 700000,
      "currency": "VND",
      "seats": 5,
      "transmission": "AUTO",
      "fuelType": "GASOLINE",
      "coverPhotoUrl": null,
      "ratingAverage": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

Note:

```text
ratingAverage is P2. P0/P1 returns null or omits it.
```

## 19.5 Host Vehicle and Listing

```http
POST   /api/v1/host/vehicles
GET    /api/v1/host/vehicles
PATCH  /api/v1/host/vehicles/{id}
DELETE /api/v1/host/vehicles/{id}

POST   /api/v1/host/listings
GET    /api/v1/host/listings?status=&page=&size=
PATCH  /api/v1/host/listings/{id}
POST   /api/v1/host/listings/{id}/submit
POST   /api/v1/host/listings/{id}/archive
POST   /api/v1/host/listings/{id}/reactivate
```

Vehicle creation behavior:

```text
New vehicles are ACTIVE by default unless host explicitly saves as DRAFT.
DRAFT vehicles cannot be linked to ACTIVE listings.
```

Vehicle DELETE behavior:

```text
Soft delete/archive only.
Archive all non-ARCHIVED listings first in the same transaction if archive preconditions pass.
Reject if any HELD, CONFIRMED, or IN_PROGRESS booking exists for any related listing.
```

## 19.6 Admin Listing and User Management

```http
GET  /api/v1/admin/listings?status=&hostId=&city=&page=&size=
GET  /api/v1/admin/listings/{id}
POST /api/v1/admin/listings/{id}/approve
POST /api/v1/admin/listings/{id}/reject
POST /api/v1/admin/listings/{id}/suspend
POST /api/v1/admin/listings/{id}/reactivate

GET  /api/v1/admin/users?status=&role=&page=&size=
```

## 19.7 Availability

Public/customer:

```http
GET /api/v1/listings/{id}/availability?from=2026-06-01&to=2026-06-30
```

Host full view:

```http
GET /api/v1/host/listings/{id}/availability?from=2026-06-01&to=2026-06-30
```

Mutation:

```http
POST /api/v1/host/listings/{id}/availability/block
POST /api/v1/host/listings/{id}/availability/unblock
```

## 19.8 Booking

```http
POST  /api/v1/bookings
GET   /api/v1/bookings/me?status=&page=&size=
GET   /api/v1/bookings/{id}
POST  /api/v1/bookings/{id}/cancel
PATCH /api/v1/bookings/{id}
```

Create booking request:

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

Booking patch request:

```json
{
  "pickupLocation": "New pickup location",
  "returnLocation": "New return location"
}
```

Patch can update only:

```text
pickupLocation
returnLocation
```

Patch cannot update:

```text
listingId
pickupDate
returnDate
status
priceSnapshot
policySnapshot
customerId
hostId
```

Cancel request:

```json
{
  "reason": "Change of plan"
}
```

Required header for mutation:

```http
Idempotency-Key: 8b71f8d2-9e1d-4f7a-bbe6-334c3816df91
```

## 19.9 Payment

```http
POST /api/v1/bookings/{id}/payments/authorize
GET  /api/v1/bookings/{id}/payments
POST /api/v1/payments/{paymentId}/capture
POST /api/v1/payments/{paymentId}/void
POST /api/v1/payments/{paymentId}/refund
```

Authorize response:

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

All payment mutation APIs require Idempotency-Key.

## 19.10 Host Booking Approval

P1:

```http
GET  /api/v1/host/bookings?status=&page=&size=
POST /api/v1/host/bookings/{id}/approve
POST /api/v1/host/bookings/{id}/reject
```

## 19.11 Files and Listing Photos

P1/P2:

```http
POST /api/v1/host/listings/{id}/photos
```

Request:

```json
{
  "fileId": "uuid",
  "sortOrder": 0,
  "isCover": true
}
```

## 19.12 Notifications, Audit, Reports

```http
GET /api/v1/notifications/me?page=&size=
GET /api/v1/admin/audit-logs?action=&targetType=&targetId=&from=&to=&page=&size=

GET /api/v1/admin/reports/revenue?from=&to=
GET /api/v1/host/reports/earnings?from=&to=
```

Reports are P2.

---

## 20. Standard Errors

| Code | HTTP Status | Meaning |
|---|---:|---|
| AUTH_INVALID_CREDENTIALS | 401 | Email or password invalid |
| AUTH_TOKEN_EXPIRED | 401 | Access token expired |
| ACCESS_DENIED | 403 | User lacks permission |
| USER_EMAIL_EXISTS | 409 | Email already registered |
| DRIVER_LICENSE_NOT_APPROVED | 403 | Customer is not eligible to book |
| ALREADY_SUBMITTED | 409 | Verification already pending/approved |
| VEHICLE_NOT_FOUND | 404 | Vehicle not found |
| VEHICLE_ARCHIVE_NOT_ALLOWED | 409 | Vehicle cannot be archived |
| LISTING_NOT_FOUND | 404 | Listing not found or not visible |
| LISTING_NOT_AVAILABLE | 409 | Listing unavailable for selected dates |
| BOOKING_OVERLAP_CUSTOMER | 409 | Customer has overlapping booking |
| BOOKING_INVALID_STATUS | 409 | Action not allowed for current booking status |
| IDEMPOTENCY_KEY_REQUIRED | 400 | Idempotency-Key required |
| IDEMPOTENCY_KEY_CONFLICT | 409 | Same key used with different body |
| REQUEST_ALREADY_PROCESSING | 409 | Request with same key is still processing |
| PAYMENT_FAILED | 402 | Payment operation failed |
| PAYMENT_VOID_RETRY_REQUIRED | 202 | Cancellation done but void retry required |
| VALIDATION_ERROR | 400 | Request validation failed |
| TOO_MANY_REQUESTS | 429 | Rate limit exceeded |
| INTERNAL_ERROR | 500 | Unexpected server error |

Standard error response:

```json
{
  "code": "LISTING_NOT_AVAILABLE",
  "message": "Listing is not available for the selected date range.",
  "details": [
    {
      "field": "pickupDate",
      "message": "pickupDate must be before returnDate"
    }
  ],
  "correlationId": "req-20260509-0001"
}
```

---

# 21. Database Model

This section defines implementation-ready schema direction. Exact SQL is maintained in Flyway migrations.

## 21.1 auth_users

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | User ID |
| email | VARCHAR(120) UNIQUE NOT NULL | Login email |
| password_hash | VARCHAR(255) NOT NULL | BCrypt hash |
| status | VARCHAR(20) NOT NULL | ACTIVE, SUSPENDED, DELETED |
| email_verified | BOOLEAN NOT NULL DEFAULT false | Email verification flag |
| last_login_at | TIMESTAMPTZ NULL | Last login |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |

## 21.2 user_roles

| Column | Type / Constraint | Description |
|---|---|---|
| user_id | UUID FK auth_users(id) | User |
| role | VARCHAR(20) NOT NULL | CUSTOMER, HOST, ADMIN |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| PK | (user_id, role) | Unique assignment |

## 21.3 refresh_tokens

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Token ID |
| user_id | UUID FK auth_users(id) | User |
| token_hash | VARCHAR(255) UNIQUE NOT NULL | Token hash |
| expires_at | TIMESTAMPTZ NOT NULL | Expiry |
| revoked_at | TIMESTAMPTZ NULL | Revocation time |
| replaced_by_token_id | UUID NULL | Rotation chain |
| created_at | TIMESTAMPTZ NOT NULL | Created time |

## 21.4 user_profiles

| Column | Type / Constraint | Description |
|---|---|---|
| user_id | UUID PK/FK auth_users(id) | User |
| full_name | VARCHAR(120) NOT NULL | Full name |
| phone | VARCHAR(30) NULL | Phone |
| date_of_birth | DATE NULL | DOB |
| address_line | TEXT NULL | Address |
| driver_verification_status | VARCHAR(20) NOT NULL | NOT_SUBMITTED, PENDING, APPROVED, REJECTED, EXPIRED |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |

## 21.5 driver_verifications

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Verification ID |
| customer_id | UUID FK auth_users(id) | Customer |
| license_number_encrypted | TEXT NOT NULL | Encrypted license |
| license_number_hash | VARCHAR(128) NOT NULL | Hash for unique lookup |
| license_expiry_date | DATE NOT NULL | Expiry |
| document_file_id | UUID FK files(id) | Document |
| status | VARCHAR(20) NOT NULL | PENDING, APPROVED, REJECTED, EXPIRED |
| reviewed_by | UUID FK auth_users(id) NULL | Admin |
| review_reason | TEXT NULL | Review reason |
| reviewed_at | TIMESTAMPTZ NULL | Review time |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |

## 21.6 vehicles

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Vehicle ID |
| host_id | UUID FK auth_users(id) | Owner |
| category | VARCHAR(30) NOT NULL | SEDAN, SUV, HATCHBACK, MPV, PICKUP, LUXURY, VAN |
| make | VARCHAR(60) NOT NULL | Manufacturer |
| model | VARCHAR(60) NOT NULL | Model |
| year | INT NOT NULL | Year |
| plate_number_encrypted | TEXT NOT NULL | Encrypted plate |
| plate_number_hash | VARCHAR(128) UNIQUE NOT NULL | Plate hash |
| vin_encrypted | TEXT NULL | Encrypted VIN |
| vin_hash | VARCHAR(128) UNIQUE NULL | VIN hash |
| transmission | VARCHAR(20) NOT NULL | AUTO, MANUAL |
| fuel_type | VARCHAR(20) NOT NULL | GASOLINE, DIESEL, EV, HYBRID |
| seats | INT NOT NULL | Seat count |
| status | VARCHAR(20) NOT NULL | DRAFT, ACTIVE, MAINTENANCE, SUSPENDED, ARCHIVED |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |

## 21.7 listings

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Listing ID |
| vehicle_id | UUID FK vehicles(id) | Vehicle |
| host_id | UUID FK auth_users(id) | Host |
| title | VARCHAR(160) NOT NULL | Title |
| description | TEXT NULL | Description |
| city | VARCHAR(80) NOT NULL | City |
| address | TEXT NULL | Address |
| latitude | NUMERIC(9,6) NULL | Latitude |
| longitude | NUMERIC(9,6) NULL | Longitude |
| base_price_per_day | NUMERIC(12,2) NOT NULL | Price |
| currency | VARCHAR(3) NOT NULL DEFAULT 'VND' | Currency |
| daily_km_limit | INT NULL | Included km |
| instant_book | BOOLEAN NOT NULL DEFAULT true | Instant booking |
| cancellation_policy | VARCHAR(30) NOT NULL | FLEXIBLE, MODERATE, STRICT |
| status | VARCHAR(30) NOT NULL | DRAFT, PENDING_APPROVAL, ACTIVE, SUSPENDED, ARCHIVED |
| version | INT NOT NULL DEFAULT 0 | Optimistic lock |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |

## 21.8 availability_calendar

| Column | Type / Constraint | Description |
|---|---|---|
| listing_id | UUID FK listings(id) | Listing |
| available_date | DATE NOT NULL | Date |
| status | VARCHAR(20) NOT NULL | FREE, HOLD, BOOKED, BLOCKED |
| hold_token | UUID NULL | Hold token |
| hold_expires_at | TIMESTAMPTZ NULL | Hold expiry |
| booking_id | UUID FK bookings(id) NULL | Related booking |
| version | INT NOT NULL DEFAULT 0 | Optimistic version |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |
| PK | (listing_id, available_date) | One row per listing/date |

## 21.9 extras

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Extra ID |
| listing_id | UUID FK listings(id) | Listing |
| name | VARCHAR(80) NOT NULL | Name |
| pricing_type | VARCHAR(20) NOT NULL | PER_DAY, PER_TRIP |
| price | NUMERIC(12,2) NOT NULL | Price |
| active | BOOLEAN NOT NULL DEFAULT true | Active |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |

## 21.10 bookings

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Booking ID |
| customer_id | UUID FK auth_users(id) | Customer |
| host_id | UUID FK auth_users(id) | Host snapshot |
| listing_id | UUID FK listings(id) | Listing |
| pickup_date | DATE NOT NULL | Start date inclusive |
| return_date | DATE NOT NULL | End date exclusive |
| status | VARCHAR(30) NOT NULL | Booking status |
| hold_token | UUID NULL | Hold token |
| hold_expires_at | TIMESTAMPTZ NULL | Hold expiry |
| host_approval_expires_at | TIMESTAMPTZ NULL | P1 approval expiry |
| pickup_location | TEXT NULL | Pickup address |
| return_location | TEXT NULL | Return address |
| price_snapshot | JSONB NOT NULL | Price snapshot |
| policy_snapshot | JSONB NOT NULL | Policy snapshot |
| cancellation_reason | VARCHAR(500) NULL | Cancellation reason, sanitized and max 500 characters |
| version | INT NOT NULL DEFAULT 0 | Optimistic lock |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |

## 21.11 booking_extras

| Column | Type / Constraint | Description |
|---|---|---|
| booking_id | UUID FK bookings(id) | Booking |
| extra_id | UUID FK extras(id) | Extra |
| quantity | INT NOT NULL | Quantity |
| price_snapshot | NUMERIC(12,2) NOT NULL | Price at booking time |
| PK | (booking_id, extra_id) | Unique extra per booking |

## 21.12 idempotency_keys

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | ID |
| user_id | UUID FK auth_users(id) | Caller |
| scope | VARCHAR(80) NOT NULL | Operation scope |
| key | VARCHAR(120) NOT NULL | UUID-v4 string |
| request_hash | VARCHAR(128) NOT NULL | Hash of request body |
| status | VARCHAR(20) NOT NULL | PROCESSING, COMPLETED, FAILED |
| response_status | INT NULL | Original response status |
| response_body | JSONB NULL | Original response body |
| locked_until | TIMESTAMPTZ NULL | Processing lock expiry |
| expires_at | TIMESTAMPTZ NOT NULL | Retention expiry |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |
| UNIQUE | (user_id, scope, key) | Idempotency key |

## 21.13 booking_payments

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Payment aggregate |
| booking_id | UUID FK bookings(id) UNIQUE | Booking |
| status | VARCHAR(30) NOT NULL | UNPAID, AUTHORIZED, CAPTURED, PARTIALLY_REFUNDED, REFUNDED, VOIDED, FAILED |
| authorized_amount | NUMERIC(12,2) NOT NULL DEFAULT 0 | Authorized |
| captured_amount | NUMERIC(12,2) NOT NULL DEFAULT 0 | Captured |
| refunded_amount | NUMERIC(12,2) NOT NULL DEFAULT 0 | Refunded |
| currency | VARCHAR(3) NOT NULL DEFAULT 'VND' | Currency |
| version | INT NOT NULL DEFAULT 0 | Optimistic lock |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |

## 21.14 payment_transactions

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Transaction |
| booking_payment_id | UUID FK booking_payments(id) | Payment aggregate |
| booking_id | UUID FK bookings(id) | Booking |
| type | VARCHAR(20) NOT NULL | AUTHORIZE, CAPTURE, VOID, REFUND |
| status | VARCHAR(20) NOT NULL | PENDING, SUCCEEDED, FAILED |
| amount | NUMERIC(12,2) NOT NULL | Amount |
| currency | VARCHAR(3) NOT NULL | Currency |
| provider | VARCHAR(40) NOT NULL | STUB, VNPAY, MOMO |
| provider_ref | VARCHAR(120) NULL | Provider reference |
| idempotency_key_id | UUID FK idempotency_keys(id) NULL | Idempotency |
| error_code | VARCHAR(80) NULL | Failure code |
| error_message | TEXT NULL | Failure message |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |

## 21.15 files

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | File ID |
| owner_id | UUID FK auth_users(id) | Uploader |
| bucket | VARCHAR(80) NOT NULL | Bucket |
| object_key | VARCHAR(255) NOT NULL | Object key |
| original_name | VARCHAR(255) NOT NULL | Original filename |
| content_type | VARCHAR(100) NOT NULL | MIME type |
| size_bytes | BIGINT NOT NULL | Size |
| file_purpose | VARCHAR(40) NOT NULL | LISTING_PHOTO, LICENSE, TRIP_PHOTO, DOCUMENT |
| checksum | VARCHAR(128) NULL | Checksum |
| storage_status | VARCHAR(20) NOT NULL | PENDING, UPLOADED, DELETED |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |
| deleted_at | TIMESTAMPTZ NULL | Deleted time |

## 21.16 listing_photos

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Listing photo ID |
| listing_id | UUID FK listings(id) | Listing |
| file_id | UUID FK files(id) | File |
| sort_order | INT NOT NULL | Gallery order |
| is_cover | BOOLEAN NOT NULL DEFAULT false | Cover flag |
| created_at | TIMESTAMPTZ NOT NULL | Created time |

## 21.17 notifications

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Notification ID |
| user_id | UUID FK auth_users(id) | Recipient |
| type | VARCHAR(40) NOT NULL | Notification type |
| title | VARCHAR(160) NOT NULL | Title |
| message | TEXT NOT NULL | Message |
| read_at | TIMESTAMPTZ NULL | Read time |
| delivery_status | VARCHAR(20) NOT NULL | PENDING, SENT, FAILED |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |

## 21.18 audit_logs

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Audit ID |
| actor_id | UUID FK auth_users(id) NULL | Actor |
| action | VARCHAR(60) NOT NULL | Action |
| target_type | VARCHAR(60) NOT NULL | Target type |
| target_id | UUID NULL | Target ID |
| before_value | JSONB NULL | Sanitized before snapshot |
| after_value | JSONB NULL | Sanitized after snapshot |
| reason | TEXT NULL | Reason |
| ip_address | VARCHAR(60) NULL | IP |
| correlation_id | VARCHAR(80) NULL | Request trace ID |
| created_at | TIMESTAMPTZ NOT NULL | Created time |

## 21.19 outbox_events

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Event ID |
| aggregate_type | VARCHAR(60) NOT NULL | Booking, Payment, Listing |
| aggregate_id | UUID NOT NULL | Aggregate ID |
| event_type | VARCHAR(80) NOT NULL | Event type |
| payload | JSONB NOT NULL | Event payload |
| status | VARCHAR(20) NOT NULL | NEW, PUBLISHED, FAILED |
| retry_count | INT NOT NULL DEFAULT 0 | Retries |
| next_retry_at | TIMESTAMPTZ NULL | Next retry |
| processed_at | TIMESTAMPTZ NULL | Published time |
| created_at | TIMESTAMPTZ NOT NULL | Created time |
| updated_at | TIMESTAMPTZ NOT NULL | Updated time |

## 21.20 booking_timeline

| Column | Type / Constraint | Description |
|---|---|---|
| id | UUID PK | Timeline ID |
| booking_id | UUID FK bookings(id) | Booking |
| actor_id | UUID FK auth_users(id) NULL | Human actor |
| actor_type | VARCHAR(20) NOT NULL | USER, SYSTEM |
| event_type | VARCHAR(60) NOT NULL | Event type |
| message | TEXT NOT NULL | Human-readable message |
| metadata | JSONB NULL | Metadata |
| created_at | TIMESTAMPTZ NOT NULL | Created time |

---

# 22. DB Constraints and Indexes

## 22.1 Required Constraints

```sql
ALTER TABLE auth_users
ADD CONSTRAINT uq_auth_users_email UNIQUE (email);

ALTER TABLE auth_users
ADD CONSTRAINT chk_auth_users_status
CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'));

ALTER TABLE user_roles
ADD CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role);

ALTER TABLE vehicles
ADD CONSTRAINT uq_vehicles_plate_hash UNIQUE (plate_number_hash);

ALTER TABLE vehicles
ADD CONSTRAINT uq_vehicles_vin_hash UNIQUE (vin_hash);

ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_seats CHECK (seats > 0);

ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_year CHECK (year >= 1990);

ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_status
CHECK (status IN ('DRAFT', 'ACTIVE', 'MAINTENANCE', 'SUSPENDED', 'ARCHIVED'));

ALTER TABLE listings
ADD CONSTRAINT chk_listings_status
CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED', 'ARCHIVED'));

CREATE UNIQUE INDEX uq_listings_one_active_per_vehicle
ON listings(vehicle_id)
WHERE status = 'ACTIVE';

ALTER TABLE availability_calendar
ADD CONSTRAINT pk_availability_calendar PRIMARY KEY (listing_id, available_date);

ALTER TABLE availability_calendar
ADD CONSTRAINT fk_availability_booking
FOREIGN KEY (booking_id) REFERENCES bookings(id);

ALTER TABLE availability_calendar
ADD CONSTRAINT chk_availability_status
CHECK (status IN ('FREE', 'HOLD', 'BOOKED', 'BLOCKED'));

ALTER TABLE bookings
ADD CONSTRAINT chk_bookings_date_range CHECK (pickup_date < return_date);

ALTER TABLE idempotency_keys
ADD CONSTRAINT uq_idempotency_scope_key UNIQUE (user_id, scope, key);

ALTER TABLE idempotency_keys
ADD CONSTRAINT chk_idempotency_scope
CHECK (scope IN (
  'CREATE_BOOKING',
  'CANCEL_BOOKING',
  'AUTHORIZE_PAYMENT',
  'CAPTURE_PAYMENT',
  'VOID_PAYMENT',
  'REFUND_PAYMENT',
  'HOST_APPROVE_BOOKING',
  'HOST_REJECT_BOOKING'
));

CREATE UNIQUE INDEX uq_driver_verification_active
ON driver_verifications(customer_id)
WHERE status IN ('PENDING', 'APPROVED');

ALTER TABLE booking_payments
ADD CONSTRAINT uq_booking_payments_booking UNIQUE (booking_id);

ALTER TABLE booking_payments
ADD CONSTRAINT chk_authorized_amount_non_negative
CHECK (authorized_amount >= 0);

ALTER TABLE booking_payments
ADD CONSTRAINT chk_booking_payment_amounts CHECK (
  authorized_amount >= 0
  AND captured_amount >= 0
  AND refunded_amount >= 0
  AND captured_amount <= authorized_amount
  AND refunded_amount <= captured_amount
);

ALTER TABLE payment_transactions
ADD CONSTRAINT pk_payment_transactions PRIMARY KEY (id);

ALTER TABLE payment_transactions
ADD CONSTRAINT chk_payment_type
CHECK (type IN ('AUTHORIZE', 'CAPTURE', 'VOID', 'REFUND'));

ALTER TABLE payment_transactions
ADD CONSTRAINT chk_payment_status
CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'));

ALTER TABLE payment_transactions
ADD CONSTRAINT chk_payment_amount_positive
CHECK (amount > 0);

ALTER TABLE extras
ADD CONSTRAINT chk_extras_price CHECK (price >= 0);

ALTER TABLE booking_extras
ADD CONSTRAINT chk_booking_extras_quantity CHECK (quantity > 0);

ALTER TABLE reviews
ADD CONSTRAINT uq_reviews_booking_reviewer UNIQUE (booking_id, reviewer_id);

ALTER TABLE reviews
ADD CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5);

ALTER TABLE files
ADD CONSTRAINT uq_files_bucket_object_key UNIQUE (bucket, object_key);

ALTER TABLE files
ADD CONSTRAINT chk_files_size CHECK (size_bytes > 0);

ALTER TABLE files
ADD CONSTRAINT chk_files_status CHECK (storage_status IN ('PENDING', 'UPLOADED', 'DELETED'));

ALTER TABLE files
ADD CONSTRAINT chk_files_purpose
CHECK (file_purpose IN ('LISTING_PHOTO', 'LICENSE', 'TRIP_PHOTO', 'DOCUMENT'));

ALTER TABLE notifications
ADD CONSTRAINT fk_notifications_user
FOREIGN KEY (user_id) REFERENCES auth_users(id);

ALTER TABLE notifications
ADD CONSTRAINT chk_notifications_delivery_status
CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED'));

ALTER TABLE listing_photos
ADD CONSTRAINT uq_listing_photos_file UNIQUE (listing_id, file_id);

ALTER TABLE listing_photos
ADD CONSTRAINT uq_listing_photos_sort UNIQUE (listing_id, sort_order);

ALTER TABLE listing_photos
ADD CONSTRAINT chk_listing_photos_sort CHECK (sort_order >= 0);

CREATE UNIQUE INDEX uq_listing_photos_cover
ON listing_photos(listing_id)
WHERE is_cover = true;

ALTER TABLE booking_timeline
ADD CONSTRAINT fk_timeline_booking
FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE;

ALTER TABLE outbox_events
ADD CONSTRAINT chk_outbox_status
CHECK (status IN ('NEW', 'PUBLISHED', 'FAILED'));

ALTER TABLE outbox_events
ADD CONSTRAINT chk_outbox_retry_count
CHECK (retry_count >= 0);
```

## 22.2 Required Indexes

```sql
CREATE INDEX idx_auth_users_status_created
ON auth_users(status, created_at DESC);

CREATE INDEX idx_vehicles_status_created
ON vehicles(status, created_at DESC);

CREATE INDEX idx_vehicles_host_status
ON vehicles(host_id, status);

CREATE INDEX idx_listings_status_city_price
ON listings(status, city, base_price_per_day);

CREATE INDEX idx_listings_host_status
ON listings(host_id, status);

CREATE INDEX idx_listings_vehicle_status
ON listings(vehicle_id, status);

CREATE INDEX idx_availability_listing_date_status
ON availability_calendar(listing_id, available_date, status);

CREATE INDEX idx_availability_hold_expiry
ON availability_calendar(status, hold_expires_at);

CREATE INDEX idx_bookings_customer_period_status
ON bookings(customer_id, pickup_date, return_date, status);

CREATE INDEX idx_bookings_listing_period_status
ON bookings(listing_id, pickup_date, return_date, status);

CREATE INDEX idx_bookings_status_hold_expiry
ON bookings(status, hold_expires_at);

CREATE INDEX idx_driver_verifications_expiry
ON driver_verifications(status, license_expiry_date);

CREATE INDEX idx_idempotency_status_locked_until
ON idempotency_keys(status, locked_until);

CREATE INDEX idx_booking_payments_booking_status
ON booking_payments(booking_id, status);

CREATE INDEX idx_payment_transactions_booking_created
ON payment_transactions(booking_id, created_at DESC);

CREATE INDEX idx_notifications_user_created
ON notifications(user_id, created_at DESC);

CREATE INDEX idx_notifications_user_read
ON notifications(user_id, read_at);

CREATE INDEX idx_audit_logs_actor_created
ON audit_logs(actor_id, created_at DESC);

CREATE INDEX idx_audit_logs_target
ON audit_logs(target_type, target_id);

CREATE INDEX idx_audit_logs_action_created
ON audit_logs(action, created_at DESC);

CREATE INDEX idx_outbox_status_retry
ON outbox_events(status, next_retry_at);

CREATE INDEX idx_outbox_aggregate
ON outbox_events(aggregate_type, aggregate_id, created_at);

CREATE INDEX idx_booking_timeline_booking_created
ON booking_timeline(booking_id, created_at);
```

---

# 23. Transaction Boundaries and Concurrency

## 23.1 TX-01 — Create Booking

```text
@Transactional
createBooking()
```

Order:

```text
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
```

Lock SQL:

```sql
SELECT *
FROM availability_calendar
WHERE listing_id = :listingId
  AND available_date >= :pickupDate
  AND available_date < :returnDate
ORDER BY available_date ASC
FOR UPDATE;
```

## 23.2 TX-02 — Authorize Payment

```text
@Transactional
authorizePayment()
```

Order:

```text
1. Lock idempotency key with scope AUTHORIZE_PAYMENT using FOR UPDATE.
2. Lock booking row using FOR UPDATE.
3. Validate booking.status = HELD.
4. Validate booking.hold_expires_at > now.
5. Lock booking_payments row using FOR UPDATE.
6. Lock availability rows for [pickupDate, returnDate) using SELECT FOR UPDATE ordered by available_date ASC.
7. Validate all availability rows have:
      status = HOLD
      booking_id = current booking id
8. Create payment transaction AUTHORIZE/SUCCEEDED.
9. If listing.instantBook = true:
      booking -> CONFIRMED
      availability HOLD -> BOOKED
      booking_payment -> AUTHORIZED
10. If listing.instantBook = false:
      booking -> PENDING_HOST_APPROVAL
      host_approval_expires_at = now + 24 hours
      booking_payment -> AUTHORIZED
11. Write booking timeline.
12. Write audit log.
13. Write outbox event.
14. Commit.
```

SQL:

```sql
SELECT *
FROM availability_calendar
WHERE listing_id = :listingId
  AND available_date >= :pickupDate
  AND available_date < :returnDate
ORDER BY available_date ASC
FOR UPDATE;
```

Failure:

```text
If any row is not HOLD or belongs to another booking:
409 BOOKING_INVALID_STATUS
```

## 23.3 TX-03 — Expire HELD Booking

```text
1. Query expired HELD bookings with FOR UPDATE SKIP LOCKED.
2. Process in batches of 100.
3. For each booking:
      lock booking
      lock availability rows
      re-check status = HELD
      booking -> EXPIRED
      availability HOLD -> FREE
      timeline/outbox
4. Commit per batch.
```

SQL:

```sql
SELECT *
FROM bookings
WHERE status = 'HELD'
  AND hold_expires_at < :now
ORDER BY id
LIMIT :batchSize
FOR UPDATE SKIP LOCKED;
```

## 23.4 TX-04 — Cancel Booking

```text
1. Lock idempotency key scope CANCEL_BOOKING.
2. Lock booking FOR UPDATE.
3. Validate actor permission.
4. Validate status allows cancellation.
5. Lock booking_payment if exists.
6. Lock availability rows.
7. Apply cancellation policy.
8. Void/refund/capture penalty as needed.
9. booking -> CANCELLED.
10. Release availability if before check-in.
11. Write audit/timeline/outbox.
12. Commit.
```

## 23.5 TX-05 — Cancel Booking with Partial Penalty

```text
@Transactional
cancelBookingWithPenalty()
```

Order:

```text
1. Lock idempotency key scope CANCEL_BOOKING.
2. Lock booking row FOR UPDATE.
3. Lock booking_payments row FOR UPDATE.
4. Lock availability rows FOR UPDATE.
5. Validate booking.status = CONFIRMED.
6. Validate payment.status = AUTHORIZED.
7. Calculate refundPercent and penaltyAmount.
8. If penaltyAmount > 0:
      CAPTURE penaltyAmount.
9. VOID remaining authorization.
10. Update booking_payments aggregate.
11. booking -> CANCELLED.
12. availability -> FREE.
13. Write audit/timeline/outbox.
14. Commit.
```

Failure handling:

```text
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

## 23.6 TX-06 — Host Approve/Reject

Host approval:

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

Host rejection:

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

## 23.7 TX-07 — Payment State Mutation

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

## 23.8 TX-08 — Driver Verification Expiry Job

```text
1. Find driver_verifications where status IN ('PENDING', 'APPROVED')
   and license_expiry_date < current_date.
2. Process in batches of 100 with FOR UPDATE SKIP LOCKED if needed.
3. For each row:
      lock row FOR UPDATE
      update driver_verifications.status = EXPIRED
      update user_profiles.driver_verification_status = EXPIRED
      create notification
      create audit log/system event
4. Existing bookings remain unchanged.
```

## 23.9 TX-09 — Listing Submit

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

If no row returned:

```text
409 BOOKING_INVALID_STATUS
```

## 23.10 TX-10 — Vehicle Archive

```text
1. Lock vehicle row FOR UPDATE.
2. Validate vehicle.host_id = authUserId.
3. Query all listings for this vehicle.
4. Query bookings for all related listings.
5. If any booking status is HELD, CONFIRMED, or IN_PROGRESS:
      reject with 409 VEHICLE_ARCHIVE_NOT_ALLOWED.
6. If all related bookings are terminal:
      archive all non-ARCHIVED listings first.
7. vehicle.status -> ARCHIVED.
8. Write audit log.
9. Commit.

All listing archive operations and vehicle archive occur in the same transaction.
```

## 23.11 TX-11 — Vehicle Suspend / Maintenance

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

## 23.12 TX-12 — Booking Patch

```text
1. Lock booking row FOR UPDATE.
2. Validate customer owns booking.
3. Validate status in HELD, PENDING_HOST_APPROVAL, CONFIRMED.
4. Update only pickupLocation and/or returnLocation.
5. Write timeline/audit if values changed.
6. commit.
```

## 23.13 Idempotency Concurrency

Idempotency row must be locked with `FOR UPDATE`.

```sql
SELECT *
FROM idempotency_keys
WHERE user_id = :userId
  AND scope = :scope
  AND key = :key
FOR UPDATE;
```

Behavior:

```text
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

# 24. Security and Authorization

## 24.1 Resource-Level Authorization

Role checks are not enough.

| Endpoint group | Required resource-level check |
|---|---|
| Customer booking detail | `booking.customer_id = authUserId` |
| Host booking list/detail | `booking.host_id = authUserId` |
| Host vehicle/listing operations | `vehicle.host_id = authUserId` or `listing.host_id = authUserId` |
| Host approval/rejection | `booking.host_id = authUserId` |
| Admin endpoints | Requires ADMIN role |
| Driver license document | Owner or ADMIN only |
| Trip photos | Related customer, host, or ADMIN only |
| Booking payment detail | Customer owner, host owner, or ADMIN only |
| Host availability view | Listing owner only |
| Vehicle status mutation | Vehicle owner + valid vehicle state transition |

## 24.2 Rate Limiting

P1:

| Endpoint | Limit |
|---|---|
| Login | 5 failed attempts per 15 minutes per IP/email |
| Booking creation | 10 attempts per user per hour |
| Public endpoints | 60 requests per minute per IP |

Rate limit response:

```text
429 TOO_MANY_REQUESTS
```

With:

```http
Retry-After: seconds
```

## 24.3 Sensitive Data Rules

Do not return in API responses:

```text
password_hash
refresh token hash
plate_number_encrypted
vin_encrypted
license_number_encrypted
license document object key
payment-sensitive data
```

Audit logs must store sanitized before/after snapshots only.

Free-text fields such as `cancellation_reason` must be stripped of HTML/script content before storage and must not be rendered unsanitized in notifications, audit views, or API clients.

---

# 25. File Storage Rules

| Rule | Requirement |
|---|---|
| FILE-01 | Buckets are private by default. |
| FILE-02 | Listing photos may become public/signed only after listing is ACTIVE. |
| FILE-03 | Driver license documents are visible only to owner and admins. |
| FILE-04 | Trip photos are visible only to related customer, host, and admins. |
| FILE-05 | Signed download URL default TTL is 10 minutes. |
| FILE-06 | Allowed MIME types are restricted by file purpose. |
| FILE-07 | Default max size: 10MB photos, 20MB documents. |
| FILE-08 | File metadata starts as PENDING until upload completes. |
| FILE-09 | Deleted files are soft-deleted in DB and removed from storage asynchronously. |

---

# 26. Outbox Events

P2.

## 26.1 Event Types

```text
ListingApproved
ListingSuspended
VehicleSuspended
VehicleArchived
BookingHeld
BookingConfirmed
BookingCancelled
BookingExpired
PaymentAuthorized
PaymentCaptured
PaymentVoided
PaymentRefunded
PaymentVoidRetryRequired
DriverVerificationApproved
DriverVerificationRejected
DriverVerificationExpired
TripCheckedIn
TripCheckedOut
```

## 26.2 Outbox Rule

Outbox event must be inserted in the same DB transaction as the related business state change.

Example:

```text
booking -> CONFIRMED
availability -> BOOKED
booking_payment -> AUTHORIZED
outbox event -> BookingConfirmed
```

All occur in one transaction.

---

# 27. Test Strategy

## 27.1 Unit Tests

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

## 27.2 Integration Tests with Testcontainers

| Test | Expected |
|---|---|
| concurrentBooking_sameListingSameDates_onlyOneSucceeds | Exactly 1 success, 9 conflict |
| createBooking_sameIdempotencyKey_returnsSameResponse | Same response |
| createBooking_sameKeyDifferentBody_returns409 | Conflict |
| expireHeldBooking_releasesAvailability | Booking EXPIRED, dates FREE |
| authorizePayment_locksAvailabilityAndConfirms | HELD → CONFIRMED, HOLD → BOOKED |
| authorizePayment_whenAvailabilityReleased_returns409 | Race protection |
| authorizePayment_instantBook_confirmsBooking | Booking CONFIRMED, payment AUTHORIZED |
| cancelConfirmedBooking_appliesPolicy | Payment action matches policy |
| cancelConfirmed_partialPenalty_captureThenVoid | Payment operations ordered |
| cancelConfirmed_voidFails_schedulesRetry | Recovery path created |
| hostCannotApproveOtherHostBooking | 403 |
| customerCannotReadOtherCustomerBooking | 403 or 404 |
| adminApproveListing_generatesAvailabilityRows | 365 rows generated |
| searchListings_excludesBlockedHeldBookedDates | Correct results |
| driverVerificationExpiryJob_updatesExpired | APPROVED/PENDING → EXPIRED |
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

## 27.3 Concurrency Tests

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

## 27.4 Security Tests

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

## 27.5 DB Tests

| Test | Expected |
|---|---|
| Flyway migration applies cleanly | Pass |
| Negative payment amount | DB rejects |
| Duplicate active listing for same vehicle | DB rejects |
| Duplicate plate hash | DB rejects |
| Duplicate active driver verification | DB rejects |
| Review rating outside 1–5 | DB rejects |
| Invalid availability status | DB rejects |
| Invalid payment type/status | DB rejects |
| Invalid idempotency scope | DB rejects |
| Invalid auth user status | DB rejects |
| Invalid vehicle status | DB rejects |
| Invalid listing status | DB rejects |
| Duplicate listing photo cover | DB rejects |
| Duplicate listing photo sort order | DB rejects |
| File with zero size | DB rejects |
| Invalid notification delivery status | DB rejects |
| Negative authorized amount | DB rejects |

---

# 28. Acceptance Criteria

## 28.1 P0 Acceptance

| Area | Criteria |
|---|---|
| Auth | Register/login/refresh/logout work |
| Security | 401/403 behavior is correct |
| Vehicle | Vehicle lifecycle validates transitions |
| Listing | Host creates listing, admin approves |
| Availability | ACTIVE listing has 365 availability rows |
| Search | Filters and pagination work |
| Booking | Create booking requires Idempotency-Key |
| Idempotency | Same key returns same response |
| Concurrency | 10 concurrent booking requests produce exactly 1 success |
| Expiry | HELD booking expires and releases availability |
| Scheduler | Expiry job uses bounded batch and SKIP LOCKED |
| Swagger | Core APIs visible and executable |
| Docker | API + PostgreSQL + Redis start with Docker Compose |
| Flyway | Migrations run cleanly |

## 28.2 P1 Acceptance

| Area | Criteria |
|---|---|
| Payment | Authorize/void/partial capture/refund are persisted |
| TX-02 | Authorize payment locks availability rows before HOLD → BOOKED |
| Cancellation | Policy calculation works |
| Cancellation ordering | Partial penalty executes CAPTURE then VOID remaining |
| Cancellation recovery | VOID failure creates retry/admin alert path |
| Driver verification | Approved driver gate works |
| Driver expiry | Expiry job updates expired licenses |
| Audit | Key actions appear in audit logs |
| Timeline | Booking status changes are visible |
| Notification | User can view DB notifications |
| Rate limiting | Login/booking limits work |

## 28.3 P2 Acceptance

| Area | Criteria |
|---|---|
| Trip | Check-in/check-out supported |
| Files | MinIO upload/download flow works |
| Review | Customer reviews completed bookings |
| Dispute | Admin resolves dispute |
| Outbox | Events created and published/retried |
| Reports | Admin/host report APIs work |
| CI | Tests run automatically |

---

# 29. Implementation Roadmap

## Phase 1 — Foundation

```text
Spring Boot project
Modular package structure
PostgreSQL + Redis Docker Compose
Flyway baseline
Global exception handler
Standard error response
Swagger/OpenAPI
Testcontainers setup
```

Exit criteria:

```text
App starts
Swagger opens
Flyway migrates schema
Health endpoint works
One sample integration test passes
```

## Phase 2 — Auth + User Basics

```text
Register
Login
Refresh token
Logout
JWT
BCrypt
user_roles
GET/PATCH /users/me
Resource-level auth helper
```

Exit criteria:

```text
401 for unauthenticated
403 for wrong role
Refresh token rotation works
User profile works
```

## Phase 3 — Vehicle + Listing Lifecycle

```text
Vehicle CRUD
Vehicle state machine
Vehicle archive/suspend rules
Listing CRUD
Listing submit
Admin approve/reject/suspend/reactivate
One active listing per vehicle
Availability generation on ACTIVE
```

Exit criteria:

```text
Host cannot manage another host's resources
Admin approval creates ACTIVE listing + 365 availability rows
Double submit listing returns conflict
Vehicle archive/suspend rules work
Vehicle default status and archive cascade are defined
```

## Phase 4 — Search + Availability

```text
GET /api/v1/listings with filters
Pagination
Guest/customer availability view
Host full availability view
Block/unblock dates
```

Exit criteria:

```text
Search excludes unavailable dates
Host sees full calendar
Guest sees safe public data
```

## Phase 5 — Booking Core

This is the portfolio core.

```text
Create booking
Idempotency
Pessimistic locking
Customer overlap detection
Self-booking prevention
HELD status
Expire HELD job with SKIP LOCKED
```

Exit criteria:

```text
10 concurrent requests -> exactly 1 booking success
Same idempotency key -> same response
Expired hold releases availability
Scheduler avoids duplicate processing
```

## Phase 6 — Payment Stub

```text
booking_payments
payment_transactions
Authorize
Void
Capture
Partial capture
Refund
Payment idempotency
TX-02 availability locking
```

Exit criteria:

```text
HELD -> CONFIRMED after authorization
Availability HOLD -> BOOKED with lock
Partial capture + void remaining works
No duplicate payment effect
```

## Phase 7 — Cancellation + Audit + Timeline

```text
Cancellation policy calculator
Cancel booking endpoint
CAPTURE penalty then VOID remaining
VOID failure retry path
booking_timeline
audit_logs
outbox event creation
```

Exit criteria:

```text
Cancel confirmed booking applies correct policy
Partial penalty ordering is tested
VOID failure recovery path exists
Audit log created
Timeline visible
```

## Phase 8 — Driver Verification + Hardening

```text
Driver license submission
Admin approve/reject
Duplicate guard
Daily expiry job
Booking gate
Sensitive-data filtering
Rate limiting
```

Exit criteria:

```text
Expired/unapproved driver cannot create new booking
Existing bookings remain valid
Sensitive data not leaked
```

## Phase 9 — P2 Extensions

```text
Files/listing photos
MinIO signed URLs
Trip check-in/check-out
Reviews
Disputes
Reports
Payouts
Outbox scheduler/Kafka optional
CI pipeline
README polish
```

Exit criteria:

```text
Repo is portfolio-ready
README explains trade-offs
Docker Compose starts local stack
Core tests pass in CI
```

---

# 30. README Requirements

The repository README should include:

```text
1. Problem statement
2. Feature list
3. Architecture diagram
4. Tech stack
5. Domain modules
6. Vehicle lifecycle
7. Booking flow
8. Payment flow
9. Cancellation flow
10. Concurrency strategy
11. Idempotency strategy
12. Database schema summary
13. API documentation link
14. How to run with Docker Compose
15. Demo accounts
16. Test strategy
17. Important test cases
18. Trade-offs and future improvements
```

---

# 31. Portfolio Demo Priority

The most important demo scenario:

```text
10 customers try to book the same car for the same date range.
Only one booking succeeds.
The other 9 requests return 409 LISTING_NOT_AVAILABLE.
The test runs automatically with Testcontainers and PostgreSQL.
```

This proves:

```text
database transaction skill
pessimistic locking
idempotency behavior
business rule correctness
API error handling
production-style backend thinking
```

Do not build P2 features before Phase 5 is complete.
