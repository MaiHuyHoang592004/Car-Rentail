# RentFlow API

Car rental booking system backend — modular monolith REST API.

## Current Phase

**Phase 1-5 partially implemented; Phase 6+ planned.**

The codebase is no longer a foundation-only skeleton. Backend auth/user, vehicle/listing,
availability, and booking core are implemented, with hardening/refactor work tracked in
[`docs/roadmap.md`](docs/roadmap.md). Frontend exists under `frontend/` and is partially
integrated with the API, but some public/host listing flows still use mocks.

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
├── auth          # Register, login, refresh token, logout, JWT/RBAC basics
├── user          # User profile
├── vehicle       # Vehicle management and lifecycle
├── listing       # Listing lifecycle, search, admin approval
├── availability  # Availability calendar and host block/unblock
├── booking       # Booking hold creation, cancellation, idempotency, locking
├── payment       # Payment stub (future)
├── notification  # Notifications (future)
├── audit         # Audit logging (future)
├── outbox        # Outbox events (future)
└── common       # Shared: config, exception, security, web
```

## How to Run

### Prerequisites

- Java 17
- Docker and Docker Compose
- Maven 3.9+

### Start Infrastructure

```bash
docker-compose up -d
```

### Run Application (local)

```bash
mvn spring-boot:run
```

Or with the Maven wrapper (once generated with `mvn wrapper:wrapper`):

```bash
./mvnw spring-boot:run
```

### Access Points

| URL | Description |
|---|---|
| http://localhost:8086/swagger-ui.html | API Documentation |
| http://localhost:8086/api-docs | OpenAPI JSON |
| http://localhost:8086/api/v1/health | Health Check |
| http://localhost:8086/actuator/health | Actuator Health |

## How to Run Tests

```bash
# Unit tests only (no Docker required)
# - HealthControllerTest (WebMvcTest with MockMvc)
# - RentFlowApplicationTests (context test with H2 in-memory DB)
mvn test

# Integration tests with Testcontainers (requires Docker running)
mvn verify -Pintegration-tests

# Run specific test class
mvn test -Dtest=HealthControllerTest

# All tests (requires Docker for Testcontainers)
mvn test
```

**Note on Testcontainers:**
- `mvn test` runs unit tests only - no Docker required.
- `mvn verify -Pintegration-tests` runs Testcontainers integration tests - requires Docker Desktop running.
- On Windows, ensure Docker Desktop is running and the Docker context is set correctly.

### Windows Testcontainers Troubleshooting

If Testcontainers fails to find Docker on Windows:

```powershell
# 1. Ensure Docker Desktop is running
docker info

# 2. Use desktop-linux context
docker context use desktop-linux

# 3. Remove stale testcontainers config (if exists)
Remove-Item -Force "$env:USERPROFILE\.testcontainers.properties" -ErrorAction SilentlyContinue

# 4. Unset DOCKER_HOST if pointing to wrong pipe (optional)
$env:DOCKER_HOST = ""

# 5. Run integration tests
mvn verify -Pintegration-tests

# 6. Or set Docker host explicitly (temporary fix)
$env:DOCKER_HOST = "npipe:////./pipe/docker_engine"
mvn verify -Pintegration-tests
```

**Note:** If you prefer using the Maven wrapper, generate it first with `mvn wrapper:wrapper`, then replace `mvn` with `./mvnw` in the commands above.

## Database

Migrations are in `src/main/resources/db/migration/`. Flyway runs automatically on startup.

## Configuration

| Environment Variable | Default | Description |
|---|---|---|
| DB_HOST | localhost | PostgreSQL host |
| DB_PORT | 5433 | PostgreSQL port |
| DB_NAME | rentflow | Database name |
| DB_USER | rentflow | Database user |
| DB_PASSWORD | rentflow | Database password |
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

## Implemented So Far

- [x] Foundation: Spring Boot, Docker Compose, Flyway, OpenAPI, health, Testcontainers.
- [x] Auth/user basics: register, login, refresh rotation, logout, JWT, RBAC, profile.
- [x] Vehicle/listing lifecycle: host CRUD, state machines, admin listing approval.
- [x] Search/availability: public listing search, availability calendar, host block/unblock.
- [x] Booking core: HELD booking creation, idempotency, overlap prevention, availability locking, cancel HELD booking, hold expiry scheduler.
- [x] Frontend shell: Next.js app, auth BFF, auth provider, API client, bookings and host/listing pages.

Current priority: harden Phase 1-5 correctness/security/frontend integration before building Phase 6 payment.

## Troubleshooting

### PostgreSQL Connection Issues

If you encounter `FATAL: password authentication failed for user "rentflow"` or connection refused errors:

#### 1. Reset Docker volumes (recommended first step)

```bash
docker compose down -v
docker compose up -d
```

#### 2. Verify PostgreSQL is running correctly

```bash
docker run --rm -e PGPASSWORD=rentflow postgres:16 psql -h host.docker.internal -p 5433 -U rentflow -d rentflow -c "select 1;"
```

Expected output: `1 row`

#### 3. Check for port conflicts

If port 5433 is already in use on your machine, either:
- Kill the process using that port, OR
- Change the external port in `docker-compose.yml` (e.g., `"5434:5432"`)

#### 4. Run the application

```bash
mvn spring-boot:run
```

### Common Issues

| Issue | Solution |
|---|---|
| `password authentication failed` | Reset Docker volumes with `docker compose down -v` |
| `Connection refused` | Ensure Docker Compose is running (`docker compose up -d`) |
| Port 5432/5433 in use | Stop local PostgreSQL or change Docker external port |
| Old data persists | Use `docker compose down -v` to remove volumes |

## Upcoming

- Phase 1-5 hardening: idempotency transaction boundaries, auth security baseline, frontend API integration, docs/source-of-truth cleanup.
- Phase 6: Payment — authorize, capture, void, refund.
- Phase 7: Cancellation + Audit + Timeline
- Phase 8A: Driver Verification
- Phase 8B: Hardening + Notifications + Rate Limiting
- Phase 9: P2 Extensions

## License

MIT
