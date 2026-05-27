# AGENTS.md — RentFlow / Car-Rentail

## Project identity

This repository is RentFlow API, a car rental booking backend REST API.

Tech stack:
- Java 17
- Spring Boot 3.3.0
- Maven
- PostgreSQL
- Flyway
- Spring Security
- JWT
- Redis
- Lombok
- MapStruct
- SpringDoc OpenAPI
- JUnit 5 / Mockito / Testcontainers

Architecture:
- Modular monolith REST API.
- One Spring Boot application.
- Code is organized by business modules under `com.rentflow`.
- Business logic should stay inside its owning module.
- Shared code goes under `common`, but `common` must not become a dumping ground.

## Documentation source of truth

Current source-of-truth docs:
- `AGENTS.md`
- `docs/architecture.md`
- `docs/roadmap.md`
- `docs/srs.md`

Historical or archived docs must not be treated as current implementation instructions unless the user explicitly asks.
If docs conflict with code, prefer code and report the drift.

## Important architecture docs

Read these before doing non-trivial work:
- `docs/architecture.md`
- `docs/roadmap.md`
- `docs/srs.md`
- Relevant feature docs only when they match the current task.

## Package structure

Expected high-level package structure:

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

Module convention:
- `controller`: REST endpoints only.
- `service` or `application`: business use cases.
- `entity` or `domain`: persistence/domain model.
- `repository`: database access.
- `dto`: request/response contracts.
- `mapper`: MapStruct mapping.
- `policy`: authorization or business policy.
- `event`: domain/application events.

## Module boundary rules

- A business module owns its business logic and DTOs.
- Do not put business logic into `common`.
- A module may depend on `common`.
- Avoid calling another business module's service directly.
- For cross-module reads, prefer repository/projection queries or explicit interfaces.
- Cross-module aggregate references should usually be UUID fields, not broad JPA object graphs.
- Preserve API contracts unless the task explicitly asks to change them.

## Security rules

Important files:
- `src/main/java/com/rentflow/common/config/SecurityConfig.java`
- `src/main/java/com/rentflow/common/security/JwtAuthenticationFilter.java`
- `src/main/java/com/rentflow/common/security/JwtAuthenticationEntryPoint.java`
- `src/main/java/com/rentflow/common/security/JsonAccessDeniedHandler.java`
- Auth module files under `src/main/java/com/rentflow/auth`

Current security model:
- Stateless session.
- CSRF disabled for API.
- JWT authentication filter is added before authorization.
- Public endpoints include auth endpoints, health, Swagger/OpenAPI, and public listing GET endpoints.
- Admin endpoints require `ADMIN`.
- Host endpoints require `HOST`.
- Other endpoints require authentication.
- Role checks are not enough; resource ownership checks are required in service/policy layer.

Do not:
- Expose ADMIN assignment through public API.
- Return HTML redirects for API auth errors.
- Hardcode JWT secrets.
- Weaken CORS by using `*` when credentials are enabled.
- Add public endpoints without explaining the security impact.

## Config rules

Important config:
- `src/main/resources/application.yml`

Current defaults:
- Active profile defaults to `local`.
- PostgreSQL default port is `5433`.
- JPA uses `ddl-auto: validate`.
- Flyway is enabled.
- JWT secret comes from `JWT_SECRET`.
- CORS origins come from `RENTFLOW_CORS_ALLOWED_ORIGINS`.
- Error messages and stack traces are not exposed by default.

Never commit real secrets.

## Database rules

- PostgreSQL is the main database.
- Flyway owns schema changes.
- Do not use Hibernate auto-DDL for schema creation.
- For schema changes, add a Flyway migration.
- Keep migrations deterministic and backward-readable.
- Be careful with booking, availability, payment, audit, and outbox transaction boundaries.

## Build and test commands

Use Maven.

Common commands:

```bash
mvn test
mvn clean package
mvn spring-boot:run
```

Unit tests:

```bash
mvn test
```

Integration tests may require Docker/Testcontainers:

```bash
mvn verify -Pintegration-tests
```

Before final answer after code changes:
- Mention changed files.
- Mention validation command run.
- If tests were not run, say why.

## Working style

Before editing files:
1. Identify the relevant module.
2. Read the existing controller/service/repository/entity/dto/mapper/test pattern.
3. Explain the current flow briefly.
4. Propose the smallest safe change.
5. Then modify files.

For bug fixes:
- Find root cause first.
- Prefer minimal fix.
- Add or update tests where reasonable.

For features:
- Follow existing module patterns.
- Do not introduce unnecessary new dependencies.
- Do not refactor unrelated code.
- Do not rename packages/classes unless required.
- Do not change API response format unless requested.

## Task-specific reading guide

For auth/login/JWT/CORS tasks, inspect:
- `docs/roadmap.md`
- `src/main/java/com/rentflow/common/config/SecurityConfig.java`
- `src/main/java/com/rentflow/common/security/*`
- `src/main/java/com/rentflow/auth/**`
- `src/main/resources/application.yml`
- related tests under `src/test/java/com/rentflow`

For booking tasks, inspect:
- `docs/roadmap.md`
- `src/main/java/com/rentflow/booking/**`
- `src/main/java/com/rentflow/availability/**`
- `src/main/java/com/rentflow/listing/**`
- related booking tests

For listing/vehicle tasks, inspect:
- `docs/roadmap.md`
- `src/main/java/com/rentflow/listing/**`
- `src/main/java/com/rentflow/vehicle/**`
- related tests

For architecture review:
- Start from `docs/architecture.md`
- Then inspect package/module boundaries.
