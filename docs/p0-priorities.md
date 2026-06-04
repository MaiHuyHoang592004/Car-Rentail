# P0 Priorities — RentFlow

> Historical planning note. The current codebase has already passed the original P0 scope and now tracks
> active work in [`docs/roadmap.md`](roadmap.md) and release evidence in
> [`docs/release-gate-evidence.md`](release-gate-evidence.md). Keep this file as portfolio context, not as
> an active implementation checklist.

These are the features that must be completed before any P1 or P2 work begins.

---

## P0 Must-Have (in priority order)

### 1. Foundation (Phase 1)

- [ ] Spring Boot project setup
- [ ] Modular package structure
- [ ] Docker Compose: PostgreSQL + Redis
- [ ] Flyway baseline migration
- [ ] Global exception handler + standard error response
- [ ] Swagger/OpenAPI setup
- [ ] Testcontainers setup
- [ ] Health endpoint

**Exit: App starts, Swagger opens, Flyway migrates, health check works.**

---

### 2. Auth + User (Phase 2)

- [ ] Register (`POST /api/v1/auth/register`)
- [ ] Login (`POST /api/v1/auth/login`)
- [ ] Refresh token (`POST /api/v1/auth/refresh`)
- [ ] Logout (`POST /api/v1/auth/logout`)
- [ ] JWT access token (short-lived)
- [ ] Refresh token with rotation
- [ ] BCrypt password hashing
- [ ] RBAC: CUSTOMER, HOST, ADMIN
- [ ] `GET /api/v1/users/me`
- [ ] `PATCH /api/v1/users/me`
- [ ] Resource-level auth helper

**Exit: 401/403 correct, refresh rotation works, profile works.**

---

### 3. Vehicle + Listing (Phase 3)

- [ ] Vehicle CRUD for host
- [ ] Vehicle state machine (DRAFT, ACTIVE, MAINTENANCE, SUSPENDED, ARCHIVED)
- [ ] Vehicle soft delete / archive
- [ ] Vehicle archive preconditions (all listings archived, no active bookings)
- [ ] Vehicle/listing coupling (suspend listings when vehicle suspends)
- [ ] Listing CRUD for host
- [ ] Listing submit (DRAFT -> PENDING_APPROVAL)
- [ ] Admin approve/reject/suspend/reactivate listing
- [ ] One ACTIVE listing per vehicle
- [ ] Availability generation (365 rows) when listing becomes ACTIVE
- [ ] Vehicle default ACTIVE status

**Exit: Host can't manage other's resources, admin approval creates 365 rows, double submit blocked.**

---

### 4. Search + Availability (Phase 4)

- [ ] Public listing search (`GET /api/v1/listings`) with filters
- [ ] Pagination
- [ ] Only ACTIVE listings in search
- [ ] Exclude HOLD/BOOKED dates from search
- [ ] Public availability view (hide sensitive HOLD details)
- [ ] Host full availability view
- [ ] Host block/unblock dates
- [ ] Block conflict: cannot block HOLD/BOOKED dates

**Exit: Search excludes unavailable dates, host sees full calendar.**

---

### 5. Booking Core (Phase 5) — PORTFOLIO CORE

- [ ] `POST /api/v1/bookings` with Idempotency-Key required
- [ ] Price calculation (base price + extras)
- [ ] SELECT FOR UPDATE on availability rows
- [ ] Customer overlap check (HELD, PENDING_HOST_APPROVAL, CONFIRMED)
- [ ] Self-booking prevention
- [ ] HELD status with hold_expires_at = now + 15 minutes
- [ ] `GET /api/v1/bookings/me`
- [ ] `GET /api/v1/bookings/{id}`
- [ ] `PATCH /api/v1/bookings/{id}` (location only)
- [ ] `POST /api/v1/bookings/{id}/cancel` (basic)
- [ ] Expire HELD booking job (15-min, bounded batch, SKIP LOCKED)

**Exit: 10 concurrent requests -> exactly 1 success, same idempotency key -> same response, expired hold releases availability.**

---

### 6. Docker + DevOps

- [ ] `docker-compose.yml` with API + PostgreSQL + Redis
- [ ] App starts in container
- [ ] Flyway migrations run on startup
- [ ] Environment variables for all config

---

### 7. Testcontainers Core Test

- [ ] Concurrent booking test: 10 requests, exactly 1 success
- [ ] Idempotency test: same key + same body = same response
- [ ] Idempotency test: same key + different body = 409
- [ ] Expire HELD test: releases availability

---

## Don't Do in P0

These are explicitly P1 or P2. Do not implement in P0:

- Payment stub (authorize, void, capture, refund)
- Cancellation policy calculator
- Driver verification
- Audit logging
- Booking timeline
- Database notifications
- Rate limiting
- Files metadata
- Listing photos
- Trip lifecycle
- Reviews
- Disputes
- Reports
- Payouts
- Outbox / Kafka
- CI pipeline
- MinIO integration
- Observability (metrics, structured logs)

---

## Portfolio Proof Checklist

The demo that proves your skill:

```
[ ] 10 customers try to book the same listing for the same date
[ ] Exactly 1 succeeds
[ ] 9 get 409 LISTING_NOT_AVAILABLE
[ ] Test runs automatically with Testcontainers
[ ] Test uses real PostgreSQL
```

This proves:
- Database transaction skill (SELECT FOR UPDATE)
- Idempotency behavior
- Pessimistic locking
- Business rule correctness
- API error handling
- Production-style backend thinking
