# Roadmap — RentFlow Implementation

Implementation phases in recommended order.

---

## Phase 1 — Foundation

```
- Spring Boot project
- Modular package structure
- PostgreSQL + Redis Docker Compose
- Flyway V1: auth + user tables only (Option A)
- Global exception handler
- Standard error response
- Correlation ID filter
- Swagger/OpenAPI
- Testcontainers setup
- Health endpoint
```

**Phase 1 does NOT configure Spring Security. All endpoints are public for smoke testing.**

**Exit criteria:**

- App starts
- Swagger opens at /swagger-ui.html
- Flyway migrates auth/user tables only (no booking/payment/audit tables)
- Health endpoint returns correct JSON
- One Testcontainers integration test passes

---

## Phase 2 — Auth + User Basics

```
- Register
- Login
- Refresh token
- Logout
- JWT
- BCrypt
- user_roles
- GET/PATCH /users/me
- Resource-level auth helper
```

**Exit criteria:**

- 401 for unauthenticated
- 403 for wrong role
- Refresh token rotation works
- User profile works

---

## Phase 3 — Vehicle + Listing Lifecycle

```
- Vehicle CRUD
- Vehicle state machine
- Vehicle archive/suspend rules
- Listing CRUD
- Listing submit
- Admin approve/reject/suspend/reactivate
- One active listing per vehicle
- Availability generation on ACTIVE
```

**Exit criteria:**

- Host cannot manage another host's resources
- Admin approval creates ACTIVE listing + 365 availability rows
- Double submit listing returns conflict
- Vehicle archive/suspend rules work

---

## Phase 4 — Search + Availability

```
- GET /api/v1/listings with filters
- Pagination
- Guest/customer availability view
- Host full availability view
- Block/unblock dates
```

**Exit criteria:**

- Search excludes unavailable dates
- Host sees full calendar
- Guest sees safe public data

---

## Phase 5 — Booking Core

**This is the portfolio core.**

```
- Create booking
- Idempotency
- Pessimistic locking
- Customer overlap detection
- Self-booking prevention
- HELD status
- Expire HELD job with SKIP LOCKED
```

**Exit criteria:**

- 10 concurrent requests -> exactly 1 booking success
- Same idempotency key -> same response
- Expired hold releases availability
- Scheduler avoids duplicate processing

---

## Phase 6 — Payment Stub

```
- booking_payments
- payment_transactions
- Authorize
- Void
- Capture
- Partial capture
- Refund
- Payment idempotency
- TX-02 availability locking
```

**Exit criteria:**

- HELD -> CONFIRMED after authorization
- Availability HOLD -> BOOKED with lock
- Partial capture + void remaining works
- No duplicate payment effect

---

## Phase 7 — Cancellation + Audit + Timeline

```
- Cancellation policy calculator
- Cancel booking endpoint
- CAPTURE penalty then VOID remaining
- VOID failure retry path
- booking_timeline
- audit_logs
- outbox event creation
```

**Exit criteria:**

- Cancel confirmed booking applies correct policy
- Partial penalty ordering is tested
- VOID failure recovery path exists
- Audit log created
- Timeline visible

---

## Phase 8A — Driver Verification

```
- Driver license submission
- Admin approve/reject
- Duplicate guard
- Daily expiry job
- Booking gate
- Sensitive-data filtering
```

**Exit criteria:**

- Expired/unapproved driver cannot create new booking
- Existing bookings remain valid
- Sensitive data not leaked

---

## Phase 8B — Hardening + Notifications

```
- Rate limiting (Redis)
- Notifications
- Host approval/rejection flow
- Host approval expiry job
- Void retry admin notification
```

**Exit criteria:**

- Login/booking rate limits work
- 429 with Retry-After header
- Host can approve/reject bookings
- Admin alerted on void failure

---

## Phase 9 — P2 Extensions

```
- Files/listing photos
- MinIO signed URLs
- Trip check-in/check-out
- Reviews
- Disputes
- Reports
- Payouts
- Outbox scheduler/Kafka optional
- CI pipeline
- README polish
```

**Exit criteria:**

- Repo is portfolio-ready
- README explains trade-offs
- Docker Compose starts local stack
- Core tests pass in CI

---

## Important Notes

- **Do not build P2 features before Phase 5 is complete.**
- Phase 5 is the portfolio proof — prioritize it.
- After each phase, verify exit criteria before moving on.
- Keep `docs/phase-*.md` updated with completion status.
