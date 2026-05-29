# RentFlow API

Car rental booking system backend — modular monolith REST API.

## Current Phase

**Phase 1-9 baseline implemented; frontend payment/admin product slice is in place; hardening and release-gate stabilization remain in progress.**

Core backend flows for auth/user, vehicle/listing, availability, booking, payment,
trip lifecycle, review, dispute, notification, outbox, and reporting are present.
Frontend now includes public listing flows, host vehicle/listing/availability flows,
customer booking detail/payment authorization, and admin listing/user management.
Current focus is hardening and regression safety before release. See
[`docs/roadmap.md`](docs/roadmap.md) for the latest implementation slices.

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
├── payment       # Authorization/capture/void/refund + reconciliation state
├── trip          # Check-in/check-out lifecycle and capture trigger
├── review        # Booking review and listing rating aggregation
├── dispute       # Customer dispute and admin resolution workflow
├── notification  # In-app notification flows
├── audit         # Audit trail for sensitive actions
├── outbox        # Transactional outbox + publisher retries
├── report        # Admin revenue / host earning reports
└── common       # Shared: config, exception, security, web
```

## How to Run

### Quick Start (PowerShell / Windows)

Preferred local backend workflow:

```powershell
.\scripts\dev-backend.ps1
```

This script:
- checks Docker CLI and daemon availability
- starts `postgres` and `redis` with `docker compose up -d` when needed
- waits for `localhost:5433` and `localhost:6379`
- runs `mvnw.cmd spring-boot:run`

### Prerequisites

- Java 17
- Docker and Docker Compose
- Maven 3.9+

### Start Infrastructure

```bash
docker compose up -d
```

### Run Application (local)

The default Spring profile is `local`. After infrastructure is up, start the backend with:

```bash
mvn spring-boot:run
```

Or with the Maven wrapper (once generated with `mvn wrapper:wrapper`):

```bash
./mvnw spring-boot:run
```

Manual fallback:

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

### Access Points

| URL | Description |
|---|---|
| http://localhost:8087/swagger-ui.html | API Documentation |
| http://localhost:8087/api-docs | OpenAPI JSON |
| http://localhost:8087/api/v1/health | Health Check |
| http://localhost:8087/actuator/health | Actuator Health |

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

### Local profile defaults

The `local` profile is intended to boot without manually exporting secrets on a new machine. These defaults are for local development only and can still be overridden with environment variables:

| Setting | Local default |
|---|---|
| `JWT_SECRET` | embedded dev-only value in `application-local.yml` |
| `ENCRYPTION_SECRET_KEY` | Base64 32-byte dev key in `application-local.yml` |
| `RENTFLOW_FILE_SIGNED_URL_SECRET` | `rentflow-local-file-signed-url-secret-1234567890` |

Base config remains strict outside `local`: non-local startup still requires explicit JWT, encryption, and signed-url secrets.

### Local helper scripts

| Script | Purpose |
|---|---|
| `.\scripts\dev-preflight.ps1` | Verify Docker daemon, container state, and port reachability for PostgreSQL/Redis |
| `.\scripts\dev-backend.ps1` | Run preflight, auto-start infra if needed, then start the backend |

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
- [x] Booking core: HELD booking creation, idempotency, overlap prevention, availability locking, customer cancellation for HELD/pending/confirmed pre-pickup paths, hold expiry scheduler.
- [x] Frontend shell: Next.js app, auth BFF, auth provider, API client, public listings, host pages, booking detail, payment authorization, and admin pages.

Current priority: transaction hardening around booking cancellation/payment mutations, API/doc consistency, and release evidence.

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

Expected local health endpoint:

```text
http://localhost:8087/actuator/health
```

### Common Issues

| Issue | Solution |
|---|---|
| `password authentication failed` | Reset Docker volumes with `docker compose down -v` |
| `Connection refused` | Ensure Docker Compose is running (`docker compose up -d`) |
| `failed to connect to the docker API` | Start Docker Desktop and wait until the daemon is fully available, then rerun `.\scripts\dev-backend.ps1` |
| `Docker CLI was not found in PATH` | Install Docker Desktop or fix your PATH before running the local scripts |
| Port 5432/5433 in use | Stop local PostgreSQL or change Docker external port |
| Old data persists | Use `docker compose down -v` to remove volumes |

## Upcoming

- Finish transaction hardening for `BookingService.cancelBooking()` and related payment mutation safety.
- Refresh backend release-gate evidence after the remaining hardening slice lands.
- Continue docs/API contract synchronization as backend state machines evolve.

## License

MIT
