# Phase 01 — Foundation

## Goal

Set up the Spring Boot project skeleton with modular structure, Docker Compose, Flyway, global exception handling, and OpenAPI documentation.

## Must Implement

- [ ] Spring Boot project with Maven (Java 17)
- [ ] Modular package structure: `com.rentflow.{auth,user,vehicle,listing,availability,booking,payment,common}`
- [ ] Each module: create only package folders and `.gitkeep` placeholders
- [ ] `common/` structure: config, exception, web
- [ ] `docker-compose.yml`: PostgreSQL 16 (port 5432), Redis 7 (port 6379)
- [ ] `application.yml` with profiles: default, local, docker
- [ ] Flyway setup with `V1__init_schema.sql` — Phase 1 creates auth/user tables only
- [ ] `V1__init_schema.sql`: auth_users, user_roles, refresh_tokens, user_profiles (no booking, payment, files, notifications, audit, outbox, timeline)
- [ ] All constraints and indexes for auth/user tables from SRS section 22
- [ ] Global exception handler: `RentFlowException`, `ResourceNotFoundException`, `BusinessRuleException`, `ConcurrencyException`, `IdempotencyException`, `PaymentException`
- [ ] Standard error response format (SRS section 20)
- [ ] Correlation ID filter: read `X-Correlation-Id` header or generate UUID, return in response header
- [ ] SpringDoc OpenAPI configuration
- [ ] Testcontainers setup with PostgreSQL
- [ ] Health endpoint at `/api/v1/health`
- [ ] BaseEntity with id, createdAt, updatedAt, `@EntityListeners`
- [ ] SecurityConfig: **Phase 1 does NOT configure Spring Security.**
  - All endpoints are public in Phase 1 (for smoke testing).
  - SecurityConfig is introduced in Phase 2.
  - Swagger, actuator health, and health endpoint are left unprotected intentionally.

## Must Not Implement

- [ ] Any business logic (auth, vehicle, listing, booking, payment)
- [ ] Any Spring Security configuration (introduced in Phase 2)
- [ ] Any domain entities for future phases (auth/user tables in migration only, no JPA entities yet)
- [ ] JWT implementation
- [ ] Real payment stub
- [ ] Domain JPA entities for: booking, payment, vehicle, listing, availability, audit, notification, outbox, files

## Files/Modules Expected

```
src/main/java/com/rentflow/
├── auth/                       (empty — placeholder only)
├── user/                       (empty — placeholder only)
├── vehicle/                    (empty — placeholder only)
├── listing/                    (empty — placeholder only)
├── availability/               (empty — placeholder only)
├── booking/                    (empty — placeholder only)
├── payment/                    (empty — placeholder only)
├── notification/               (empty — placeholder only)
├── audit/                      (empty — placeholder only)
├── outbox/                     (empty — placeholder only)
└── common/
    ├── BaseEntity.java
    ├── config/
    │   ├── JacksonConfig.java
    │   └── OpenApiConfig.java
    ├── exception/
    │   ├── RentFlowException.java
    │   ├── ResourceNotFoundException.java
    │   ├── BusinessRuleException.java
    │   ├── ConcurrencyException.java
    │   ├── IdempotencyException.java
    │   ├── PaymentException.java
    │   ├── GlobalExceptionHandler.java
    │   └── CorrelationIdHelper.java
    └── web/
        ├── ErrorResponse.java
        ├── CorrelationIdFilter.java
        └── HealthController.java

src/main/resources/
├── application.yml
├── application-local.yml
├── application-docker.yml
└── db/migration/
    └── V1__init_schema.sql     (auth + user tables only)

src/test/java/com/rentflow/
├── RentFlowApplicationTests.java
└── common/web/HealthControllerTest.java

docker-compose.yml
pom.xml
```

## Flyway: Option A (Recommended)

Phase 1 creates only auth/user tables. Each subsequent phase adds its own migration:

| Migration | Tables |
|---|---|
| V1 | auth_users, user_roles, refresh_tokens, user_profiles |
| V2 | vehicles, listings, extras |
| V3 | availability_calendar |
| V4 | bookings, booking_extras, idempotency_keys |
| V5 | booking_payments, payment_transactions |
| V6 | driver_verifications, notifications, audit_logs, outbox_events, booking_timeline, files, listing_photos |

This approach keeps each phase's scope tight and prevents Cursor from generating entities for all domains at once.

## Acceptance Criteria

- [ ] `mvn spring-boot:run` starts the application
- [ ] Swagger UI accessible at `/swagger-ui.html`
- [ ] `GET /actuator/health` returns `{"status":"UP"}`
- [ ] `GET /api/v1/health` returns `{"status":"UP","service":"rentflow-api"}`
- [ ] Flyway creates auth/user tables on startup (no booking/payment/audit tables)
- [ ] Testcontainers smoke test passes (application context loads)
- [ ] No hardcoded config — all from `application.yml` / environment variables
- [ ] All module packages contain only `.gitkeep` placeholders (no domain code)

## Tests Required

- [ ] Testcontainers smoke test: application context loads with real PostgreSQL
- [ ] Health endpoint test: returns correct JSON
- [ ] Correlation ID test: returns generated ID in response header

## Notes

- Use `@EntityListeners(AuditingEntityListener.class)` for automatic createdAt/updatedAt.
- V1 migration creates only auth/user tables. Do NOT include booking, payment, vehicle, listing, availability, audit, notification, outbox, files, or timeline tables.
- Each future phase (2, 3, 4...) creates its own Flyway migration file.
- All config values must come from environment variables or `application.yml`.
- No business logic in Phase 1 — only project skeleton and minimal schema.
