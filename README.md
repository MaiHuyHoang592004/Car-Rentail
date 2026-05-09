# RentFlow API

Car rental booking system backend — modular monolith REST API.

## Current Phase

**Phase 1: Foundation** — Spring Boot skeleton, Docker Compose, Flyway, OpenAPI, Testcontainers.

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Cache/Session | Redis 7 |
| API Docs | SpringDoc OpenAPI |
| Testing | JUnit 5, Testcontainers |
| Build | Maven |

## Project Structure

```
com.rentflow
├── auth          # Authentication (future)
├── user          # User profile (future)
├── vehicle       # Vehicle management (future)
├── listing       # Listing management (future)
├── availability  # Availability calendar (future)
├── booking       # Booking creation (future)
├── payment       # Payment stub (future)
├── notification  # Notifications (future)
├── audit         # Audit logging (future)
├── outbox        # Outbox events (future)
└── common       # Shared: config, exception, security, web
```

## How to Run

### Prerequisites

- Java 21
- Docker and Docker Compose
- Maven 3.9+

### Start Infrastructure

```bash
docker-compose up -d
```

### Run Application (local)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Or with Maven wrapper:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Access Points

| URL | Description |
|---|---|
| http://localhost:8080/swagger-ui.html | API Documentation |
| http://localhost:8080/api-docs | OpenAPI JSON |
| http://localhost:8080/api/v1/health | Health Check |
| http://localhost:8080/actuator/health | Actuator Health |

## How to Run Tests

```bash
# All tests (requires Docker for Testcontainers)
mvn test

# Specific test class
mvn test -Dtest=HealthControllerTest

# With coverage
mvn test jacoco:report
```

Tests use Testcontainers to spin up a real PostgreSQL container. Requires Docker running locally.

## Database

Migrations are in `src/main/resources/db/migration/`. Flyway runs automatically on startup.

## Configuration

| Environment Variable | Default | Description |
|---|---|---|
| DB_HOST | localhost | PostgreSQL host |
| DB_PORT | 5432 | PostgreSQL port |
| DB_NAME | rentflow | Database name |
| DB_USER | rentflow | Database user |
| DB_PASSWORD | rentflow123 | Database password |
| SPRING_PROFILES_ACTIVE | local | Spring profile |

## API Response Format

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [
    { "field": "email", "message": "must not be blank" }
  ],
  "correlationId": "abc-123-def"
}
```

## Implemented in Phase 1

- [x] Spring Boot project structure
- [x] Modular package skeleton (12 module packages)
- [x] Docker Compose (PostgreSQL 16 + Redis 7)
- [x] Flyway V1: auth/user tables only
- [x] Global exception handler
- [x] Standard error response format
- [x] Correlation ID filter
- [x] Swagger/OpenAPI configuration
- [x] Health endpoint at `/api/v1/health`
- [x] Testcontainers smoke test

**Phase 1 does NOT include Spring Security. All endpoints are public (for smoke testing). SecurityConfig is introduced in Phase 2.**

## Upcoming (Phase 2+)

- Phase 2: Auth — Register, login, JWT, refresh token, RBAC, SecurityConfig
- Phase 3: Vehicle + Listing — CRUD, state machine, admin approval
- Phase 4: Search + Availability — listing search, availability calendar
- Phase 5: Booking Core — create booking, idempotency, pessimistic locking
- Phase 6: Payment — authorize, capture, void, refund
- Phase 7: Cancellation + Audit + Timeline
- Phase 8A: Driver Verification
- Phase 8B: Hardening + Notifications + Rate Limiting
- Phase 9: P2 Extensions

## License

MIT
