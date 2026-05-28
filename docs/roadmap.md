# Roadmap — RentFlow Implementation & Refactor

Roadmap này thay bản phase-only cũ. Mục tiêu là giữ cả **implementation track** và **refactor/hardening track** dựa trên review của Claude đã được đối chiếu lại với code GitHub.

Quy ước trạng thái:

- **Confirmed**: có evidence trong code thật.
- **Suspected**: Claude nêu đúng hướng nhưng cần kiểm thử/đọc thêm.
- **Spec-only**: chỉ có trong SRS/docs, chưa thấy code implement.

---

## 0. Current Project State

### Backend

Code hiện tại không còn ở Phase 1 thuần. Backend đã có:

- Auth: register, login, refresh, logout, JWT, refresh token DB, BCrypt.
- User/profile basics.
- Vehicle/listing lifecycle.
- Availability generation, public/host availability, block/unblock.
- Booking core: create booking, cancel HELD booking, patch location, idempotency, customer overlap, availability locking.

=> Current practical state: **Phase 1–5 partially implemented; Phase 6+ planned**.

### Frontend

Frontend đã tồn tại trong `frontend/` với:

- Next.js + React + TypeScript.
- Auth BFF `/api/auth/*`.
- `AuthProvider` + `api-client.ts`.
- Listings/host/bookings UI.
- Public listing, host vehicle/listing/availability, booking-create, profile và host dashboard flows đã nối API.

### Recent hardening completed

- Critical refactor batch `C01-C07`: idempotency/auth hardening, production frontend API wiring, docs/code drift cleanup.
- Important cleanup batch: not-found handling, SecurityContext annotation, vehicle archive update, phone validation, ADMIN role rejection, optimistic lock handling.
- Security hardening batch: Swagger disabled in prod, explicit CORS origins, typed JWT auth errors, unauthenticated requests return 401 while wrong-role requests return 403.
- Rate-limit contract hardening `8B.2`: public GET endpoints now have test evidence for `429 + Retry-After + {code,message,correlationId}` and path normalization edge cases.
- Rate-limit integration hardening `8B.1R`: login and booking throttling now have integration evidence for `429 + Retry-After + RATE_LIMIT_EXCEEDED`.
- Phase 7 hardening updates: audit detail sanitization and listing approve/reject outbox emission are now implemented with test evidence.
- Cancellation reliability update `7.2R`: void-retry metadata persistence and idempotent 202 replay behavior are covered by integration tests.
- Phase 9 foundation `9.1-9.5`: files metadata + signed URL, trip lifecycle, reviews, disputes, reporting/payout baseline are implemented with unit/WebMvc evidence and integration tests in place.
- Outbox publisher `9.6`: scheduler + retry/backoff/max-attempt persistence has been implemented with unit coverage and integration evidence for retry progression/idempotent send behavior.
- CI/observability baseline `9.7`: GitHub Actions CI (`unit/package` + `integration profile`) and actuator metrics/prometheus exposure with secured access are now wired.

### Docs/code drift

- README/roadmap cũ nói Phase 1, nhưng code đã có SecurityConfig/AuthService/BookingService/frontend.
- Roadmap cũ chưa có refactor/hardening track.
- Java version cần đồng bộ giữa `pom.xml`, README và SRS.

### Audit table

| Issue | Category | Severity | Evidence | Status | Suggested phase |
|---|---|---:|---|---|---|
| Idempotency failure marker chưa tách transaction | Backend | Critical | `IdempotencyService.fail()` có `@Transactional` mặc định; `BookingService` có TODO `REQUIRES_NEW failure marker` | Confirmed | Critical / Phase 5 hardening |
| Auth login timing enumeration risk | Auth/Security | Critical | `AuthService.login()` trả lỗi ngay nếu email không tồn tại, chỉ BCrypt khi user tồn tại | Confirmed | Auth hardening |
| Refresh token reuse detection chưa có | Auth/Security | Critical | `RefreshTokenService.findActiveByToken()` chỉ tìm active token; revoked token reuse trả null | Confirmed | Auth hardening |
| Frontend listings search vẫn mock | Frontend | Critical | `listings-page-view.tsx` import `LISTING_CARDS` từ `@/mocks/listings` | Confirmed | Frontend API migration |
| Host listing lifecycle fake ở client | Frontend | Critical | `host-listing-detail-page-view.tsx` import `archiveListingTransition`, `submitListingTransition` từ mocks | Confirmed | Frontend API migration |
| Docs/code drift | Docs | Critical | README/roadmap cũ nói Phase 1 nhưng code có auth/booking/frontend | Confirmed | Immediate |
| Cancel booking chỉ cho `HELD` | Feature | Important | `BookingService.cancelBooking()` check `booking.getStatus() != BookingStatus.HELD` | Confirmed | Phase 7 |
| `GlobalExceptionHandler` phình to | Backend | Important | Handler riêng cho Vehicle/Listing/Booking not found, đã có `ResourceNotFoundException` | Confirmed | Backend hardening |
| Data integrity handler dò message string | Backend | Important | `message.contains("uq_listings_one_active_per_vehicle")` | Confirmed | Backend hardening |
| IdempotencyException map HTTP status bằng string code | Backend | Important | `handleIdempotency()` so sánh `ex.getCode()` để chọn 400/409 | Confirmed | Backend hardening |
| `SecurityContext` interface annotate `@Component` | Backend | Important | `@Component public interface SecurityContext` | Confirmed | Backend cleanup |
| Swagger/docs permitAll | Security | Important | `SecurityConfig` permitAll `/swagger-ui/**`, `/api-docs/**` | Confirmed | Ops hardening |
| CORS credentials không validate wildcard origin | Security | Important | `setAllowedOrigins(origins)` + `setAllowCredentials(true)` | Confirmed | Security hardening |
| `JwtAuthenticationEntryPoint` dùng reflection | Security/Exception | Important | reflection `getMethod("getCode")`; `/api/v1/host/` trả 403 | Confirmed | Auth cleanup |
| Listing search native SQL + dead Specification | Backend/Search | Important | `ListingSearchRepositoryCustomImpl` build SQL string và có `buildSpecification()` không dùng | Confirmed | Search hardening |
| Availability generation N+1-ish | Backend/Performance | Important | loop 365 ngày và gọi `existsByListingIdAndAvailableDate` từng ngày | Confirmed | Availability hardening |
| Vehicle archive save từng listing | Backend/Performance | Important | loop `listingRepository.save(listing)` | Confirmed | Vehicle hardening |
| Frontend API client singleton | Frontend | Important | globals `accessTokenGetter`, `refreshHandler` trong `api-client.ts` | Confirmed | Frontend hardening |
| Middleware chỉ check cookie tồn tại | Frontend/Auth | Important | `middleware.ts`: nếu có refresh cookie thì `NextResponse.next()` | Confirmed | Route hardening |
| Mobile nav thiếu hamburger | UX | Important | `AppShell` có nav `hidden md:flex`, chưa có replacement mobile | Confirmed | UX hardening |
| UI mix Vietnamese/English | UX | Important | Listings/host text English, nav/auth nhiều tiếng Việt | Confirmed | UX cleanup |
| Forgot/reset/change password | Auth | Important | Không thấy endpoint/code trong auth module hiện tại | Confirmed | Auth hardening |
| Email verification chưa enforce | Auth | Important | Register set `emailVerified=false`; login không check email verified | Confirmed | Auth hardening |
| JWT key rotation/JWK | Security | Nice | Chỉ thấy HS/JWT config, chưa thấy multi-key/JWK | Spec-only | Future |
| Kafka/outbox publisher | Backend/Ops | Nice | SRS/roadmap có nhưng chưa cần trước DB outbox | Spec-only | Phase 9 |
| Full i18n | UX | Nice | Cần sau khi UI text được thống nhất | Spec-only | Later |

---

## 1. Critical Refactor Before New Features

### C1. Harden idempotency failure handling

**Problem**: `fail()` chưa dùng transaction riêng; `BookingService` đã có TODO nhưng chưa đóng.

**Evidence**:

- `src/main/java/com/rentflow/common/idempotency/service/IdempotencyService.java`
- `src/main/java/com/rentflow/booking/service/BookingService.java`

**Risk**: outer transaction rollback kéo theo failure marker rollback; retry behavior có thể sai ở edge case.

**Fix direction**:

- Thêm method/service `markFailedRequiresNew(...)` dùng `Propagation.REQUIRES_NEW`.
- Gọi marker trong catch của create/cancel booking.
- Giữ replay COMPLETED và conflict different body.

**Test/verification**:

- Simulate business exception sau khi idempotency row được tạo.
- Verify row vẫn được mark FAILED dù business transaction rollback.

**Definition of Done**:

- Không còn TODO `REQUIRES_NEW failure marker`.
- Test idempotency failure/retry pass.

### C2. Auth security baseline

**Problem**: auth thiếu một số hardening tối thiểu.

**Evidence**:

- `AuthService.login()` timing risk.
- `RefreshTokenService.findActiveByToken()` chưa detect reuse revoked token.
- Register set email unverified nhưng login không enforce.
- Không có forgot/change/reset password flow.

**Risk**: brute force/timing enumeration, không detect stolen refresh token, UX/support gap.

**Fix direction**:

- Dummy BCrypt for unknown email.
- Redis rate limit login + booking create.
- Refresh token reuse detection + revoke active tokens for user/session family.
- Forgot/reset/change password minimal flow.
- Email verification gate for booking/payment or sensitive actions.

**Test/verification**:

- Login wrong email/wrong password timing roughly comparable.
- Reuse old refresh token revokes active session.
- Password change revokes old refresh tokens.

**Definition of Done**:

- Auth hardening tests pass.
- README documents current auth limitations.

### C3. Remove production dependency on frontend mocks

**Problem**: important frontend flows still use mocks and fake state transitions.

**Evidence**:

- `listings-page-view.tsx` uses `LISTING_CARDS`.
- `host-listing-detail-page-view.tsx` uses mock transition functions.

**Risk**: frontend logic drifts from backend rules; API wiring later becomes expensive.

**Fix direction**:

- Create `features/listings/api.ts`, `features/host/vehicles/api.ts`, `features/host/listings/api.ts`.
- Use React Query for server state.
- Keep mocks only behind adapter/env flag.

**Test/verification**:

- Listings page calls backend search API.
- Host listing actions call backend lifecycle endpoints.

**Definition of Done**:

- Main production page views no longer import `@/mocks/*` directly.

### C4. Align docs with code

**Problem**: docs say Phase 1, code is already beyond Phase 1.

**Evidence**:

- Old roadmap/README vs code modules.

**Risk**: AI tools and reviewers misunderstand project maturity.

**Fix direction**:

- Update README current state.
- Keep this roadmap as source of truth.
- Optionally create `docs/technical-debt.md` if backlog grows.

**Definition of Done**:

- README, roadmap, SRS do not contradict current code.

---

## 2. Backend Hardening Roadmap

### Auth/security

- Constant-time login behavior.
- Login/booking rate limiting.
- Refresh token reuse detection.
- Forgot/reset/change password.
- Email verification policy.
- Swagger protected/disabled in prod.
- CORS wildcard validation when credentials are enabled.
- `JwtAuthenticationEntryPoint` cleanup.

### Idempotency

- `REQUIRES_NEW` failure marker.
- Split idempotency validation/conflict exception or add HTTP status to exception.
- Add idempotency cleanup job later.

### Transaction boundaries and state machine

- Keep Phase 5 cancel limited to HELD but document clearly.
- Phase 7 extends cancel to `PENDING_HOST_APPROVAL` and `CONFIRMED` with payment behavior.
- Decide whether booking PATCH needs idempotency once timeline/audit exists.

### Exception handling

- Reduce per-entity exception handlers.
- Prefer one `RentFlowException` handler with exception-owned HTTP status.
- Stop parsing DB exception messages by substring where possible.

### Search/availability performance

- Clean `ListingSearchRepositoryCustomImpl`: choose native SQL path or Specification path, not both.
- Replace 365-day availability generation loop with native `generate_series` insert.
- Replace vehicle archive listing loop with batch update.

### Scheduler / audit / timeline / outbox

- Verify HELD expiry job uses bounded batches and `SKIP LOCKED`.
- Add audit/timeline in Phase 7 only for important state changes.
- Add DB outbox before considering Kafka.

---

## 3. Frontend Integration Roadmap

### Mock -> real API migration

Order:

1. Public listings search/detail.
2. Host vehicles CRUD.
3. Host listings CRUD + submit/archive/reactivate.
4. Admin listing approval.
5. Profile page.

Rules:

- Page views must not import mocks directly.
- Add API layer per feature.
- Use React Query for server state.
- Remove “static UI” banners from production views.

### Auth state

Current singleton `api-client.ts` is acceptable short-term, but should later become instance/context-based or at least expose test reset helpers.

### Route protection

- Short term: backend remains authoritative.
- Medium: central route policy table shared by `middleware.ts` and `RoleGuard`.
- Avoid fetching sensitive SSR data before role validation.

### Form validation

- Standardize new forms on Zod + React Hook Form.
- Migrate host vehicle form first.
- Keep backend Bean Validation as final authority.

### Loading/error UX

- Add shared `EmptyState`, `FormError`, `PageSkeleton`, `FilterBar`.
- Centralize `ApiError` mapping from backend code to UI behavior.

### BFF vs direct API

Decision for MVP: keep hybrid.

- Auth uses BFF because refresh token cookie is server-only.
- Other APIs can continue using direct `/api/v1` + Bearer access token.
- Revisit full BFF/proxy only when deployment/CORS becomes painful.

### UX consistency

- Vietnamese primary UI for now.
- Replace English copy in listings/host/admin pages.
- Add mobile nav drawer/hamburger.
- Do not add full i18n framework yet.

---

## 4. Feature Completion Roadmap

### Phase 1 — Foundation

**Status**: Implemented; docs need update.

Exit criteria:

- App starts.
- Swagger works in dev/local.
- Docker infra starts.
- README reflects real current phase.

### Phase 2 — Auth + User

**Status**: Mostly implemented; needs hardening.

Remaining:

- Constant-time login.
- Rate limiting.
- Refresh token reuse detection.
- Forgot/reset/change password.
- Email verification policy.

### Phase 3 — Vehicle + Listing

**Status**: Implemented enough for P0; needs cleanup.

Remaining:

- Batch archive listing update.
- Verify state machine tests.
- Normalize response/pagination where needed.
- Wire frontend host pages to API.

### Phase 4 — Search + Availability

**Status**: Backend exists; frontend still mock-heavy.

Remaining:

- Clean search repository.
- Optimize availability generation.
- Wire public search/detail frontend.

### Phase 5 — Booking Core

**Status**: Core exists; needs hardening.

Remaining:

- Idempotency failure marker. _(Done — C01)_
- Verify concurrent booking test.
- Verify HELD expiry job. _(Done — I32)_
- Document cancel limitation. _(Done — I01)_
- Idempotency cleanup scheduler. _(Done — I31)_

### Phase 6 — Payment Stub

Do after Critical Refactor.

Scope:

- `booking_payments`.
- `payment_transactions`.
- authorize/void/capture/refund stub.
- payment idempotency.
- TX-02 availability locking.

### Phase 7 — Cancellation + Audit + Timeline

Scope:

- Cancellation policy calculator.
- Cancel `PENDING_HOST_APPROVAL` and `CONFIRMED`.
- Void/refund/capture penalty behavior.
- Booking timeline.
- Audit logs.
- Minimal outbox event creation if useful.

### Phase 8 — Driver Verification + Hardening

Merge old 8A/8B.

Scope:

- Driver license submission.
- Admin approve/reject.
- Daily expiry job.
- Booking gate.
- Sensitive-data filtering.
- Notifications DB.
- Rate limits.

### Phase 9 — P2 Extensions

Status: core slices `9.1` through `9.7` are implemented in the current codebase; remaining work is provider-grade hardening and release validation.

Scope:

- Files/listing photos.
- MinIO signed URLs.
- Trip check-in/check-out.
- Reviews.
- Disputes.
- Reports.
- Payouts.
- Outbox scheduler/Kafka optional.
- CI polish.

---

## 5. Portfolio Demo Priority

1. Auth flow: register/login/refresh/protected endpoint.
2. Listing approval: host creates vehicle/listing, admin approves, availability generated.
3. Search: active listings + date availability filter.
4. Create booking: Idempotency-Key, HELD booking, HOLD availability, snapshot price/policy.
5. Concurrent booking test: 10 requests -> exactly 1 success.
6. Idempotency replay: same key/body returns same response; same key/different body conflicts.
7. Payment authorization stub: HELD -> CONFIRMED, HOLD -> BOOKED.
8. Cancellation policy: HELD release now; CONFIRMED policy in Phase 7.

---

## 6. Do Not Build Yet

Avoid these until Phase 5 hardening + basic frontend API migration are stable:

- Real payment gateway.
- Kafka before DB outbox.
- Microservices split.
- Full event sourcing.
- OAuth/social login before auth baseline is stable.
- MFA/SSO/SAML.
- Complex admin analytics.
- Payout automation.
- Surge pricing/advanced pricing engine.
- Full i18n before Vietnamese copy is consistent.
- Storybook expansion before shared components are stable.

---

## Working Rule for AI Reviews

For future Claude/Codex/ChatGPT reviews:

1. Require file path + method/component evidence.
2. Mark each issue Confirmed/Suspected/Spec-only.
3. Only Confirmed issues enter Critical.
4. Every refactor item needs test/verification.
5. Prefer small testable PRs over giant rewrites.
