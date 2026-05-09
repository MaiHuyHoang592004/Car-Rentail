# Architecture — RentFlow

## Target Architecture

**Modular monolith REST API.**

One Spring Boot application is deployed, but code is organized by business modules.

## System Context

```
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

## Package Structure

```
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

```
controller
application/service
domain/entity
repository
dto
mapper
event
policy
```

## Architecture Principles

| Principle | Requirement |
|---|---|
| API-first | All business capabilities are exposed through REST APIs. |
| Domain module ownership | Each module owns its own business logic and DTOs. |
| Transactional consistency | Booking, availability, payment, audit, and outbox updates must use clear transaction boundaries. |
| No mixed lifecycle state | Booking and payment statuses are separate. |
| Test real DB behavior | Key flows use PostgreSQL Testcontainers. |
| No app-local file storage | Files go to MinIO/S3; DB stores metadata. |
| Security by role and resource | Role checks are not enough; ownership checks are required. |

## Module Boundaries

- `common/` module: shared utilities, config, exceptions, security, pagination.
- `common/` is NOT a dumping ground — each submodule must be justified.
- Business logic belongs in its own module, not in common.
- Module may depend on `common/*`.
- Module may NOT depend on another business module's service directly.
- If booking needs listing data: go through listing's repository, not service.

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Cache/Session | Redis 7 |
| File Storage | MinIO / S3 |
| API Docs | SpringDoc OpenAPI |
| Testing | JUnit 5, Mockito, Testcontainers |
| Build | Maven |
| Container | Docker Compose |

## Key Design Decisions

| Decision | Rationale |
|---|---|
| Modular monolith | Easier to build than microservices while demonstrating clean boundaries |
| PostgreSQL | Strong transactions, row locks, JSONB, indexes, Testcontainers support |
| Day-based rental | Keeps booking and availability deterministic |
| Pessimistic locking | Best fit for double-booking prevention |
| Payment stub | Supports rental final charges and cancellation penalties without real payment gateway |
| Idempotency required | Prevents duplicate booking/payment/cancel/approval effects |
| Vehicle/listing coupling | Automatic status propagation for data consistency |
| Outbox pattern | Reliable event publishing without Kafka as prerequisite |
