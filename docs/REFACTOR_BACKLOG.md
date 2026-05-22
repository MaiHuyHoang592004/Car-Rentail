# RentFlow — Refactor & Improvement Backlog

> Mỗi issue có evidence từ code thật, risk assessment, fix direction, test strategy, effort estimate.
>
> Status: **Done** (đã đóng trong code) | **Confirmed** (thấy trong code) | **Suspected** (hợp lý, cần verify) | **Spec-only** (chỉ ở docs)
>
> Effort: **XS** (< 2h) | **S** (2-4h) | **M** (4-8h) | **L** (8-16h) | **XL** (> 16h)
>
> Last updated: 2026-05-22
>
> Completed: `C01-C07`, `I02`, `I05-I09`, `I11`, `I12`, `I15`, `I18`, `I22`, `I30`.

---

# CRITICAL (Block mọi feature mới)

---

## C01 — Idempotency Failure Marker Chưa Tách Transaction

**Status:** Done | **Area:** Backend / Correctness | **Effort:** XS | **Depends:** None

**Evidence:**
- `src/main/java/com/rentflow/common/idempotency/service/IdempotencyService.java` — method `fail()` dùng `@Transactional` mặc định (REQUIRED propagation).
- `src/main/java/com/rentflow/booking/service/BookingService.java` — TODO inline: `// TODO: Consider a REQUIRES_NEW failure marker`.

**Problem:** Outer TX rollback → `fail()` rollback theo → idempotency key KHÔNG chuyển sang FAILED → retry behavior sai spec TX-07.

**Risk:** Double-booking edge case. Portfolio demo "idempotency proof" sai.

**Fix direction:**
1. Tạo bean riêng `IdempotencyFailureMarker` với `@Transactional(propagation = REQUIRES_NEW)`.
2. BookingService catch block gọi `failureMarker.markFailed(...)` rồi rethrow.
3. Xoá TODO comment.

**Acceptance:** Outer TX rollback → idempotency key status = FAILED (query DB verify). Retry same key test pass.

---

## C02 — Login Timing Enumeration Vulnerability

**Status:** Done | **Area:** Auth / Security | **Effort:** S | **Depends:** None

**Evidence:**
- `src/main/java/com/rentflow/auth/service/AuthService.java` — method `login()`: email không tồn tại → throw ngay (~5ms). Email tồn tại password sai → BCrypt compare (~250ms).

**Problem:** Timing side-channel: attacker đo response time → email enumeration.

**Risk:** OWASP A07:2021. Social engineering vector.

**Fix direction:** Dummy BCrypt compare khi email not found:
```java
private static final String DUMMY_HASH = "$2a$10$...";
if (userOpt.isEmpty()) {
    passwordEncoder.matches(password, DUMMY_HASH); // constant time
    throw new InvalidCredentialsException(...);
}
```

**Acceptance:** Both paths execute BCrypt. Timing delta < 50ms.

---

## C03 — Refresh Token Reuse Detection Missing

**Status:** Done | **Area:** Auth / Security | **Effort:** M | **Depends:** None

**Evidence:**
- DB schema V1: column `replaced_by_token_id` trên `refresh_tokens` table.
- `AuthService.java` (hoặc `RefreshTokenService`): refresh chỉ query `status = ACTIVE`. Rotated token (REVOKED + replacedBy != null) → generic "not found" → NO family revocation.

**Problem:** Stolen rotated refresh token không trigger security alert. OWASP spec violation.

**Risk:** Token family compromise undetected.

**Fix direction:**
1. Query token by hash bất kể status.
2. Nếu REVOKED + replacedByTokenId != null → reuse → revoke ALL user tokens.
3. Log security event.

**Acceptance:** Login → refresh (A→B) → dùng A refresh → ALL tokens revoked. B cũng invalid.

---

## C04 — Login/Booking Rate Limit Missing

**Status:** Done | **Area:** Auth + Booking / Security | **Effort:** M | **Depends:** C02

**Evidence:**
- Không có `@RateLimiter`, Bucket4j, hoặc custom filter.
- `AuthController.login()` và `BookingController.createBooking()` không có throttle.

**Problem:** Brute force unlimited. Booking HOLD flood.

**Fix direction:** Redis-based sliding window. Login: 5/5min/(IP+email). Booking: 10/1min/customer. Return 429 + Retry-After.

**Acceptance:** 6th failed login → 429. 11th booking → 429. Window expire → reset.

---

## C05 — Frontend Listings Search Vẫn Mock

**Status:** Done | **Area:** Frontend / Integration | **Effort:** M | **Depends:** Backend API exist

**Evidence:**
- `frontend/src/features/listings/listings-page-view.tsx`: `import { LISTING_CARDS } from "@/mocks/listings"`
- Filter logic: `applyListingFilters(LISTING_CARDS, filters)` — client-side filter.

**Problem:** Public search không test backend API. Schema drift.

**Fix direction:** Tạo `features/listings/api.ts` với React Query. Replace mock import.

**Acceptance:** Page gọi `GET /api/v1/listings`. Filter/pagination qua API. `@/mocks/listings` not imported.

---

## C06 — Host Listing Lifecycle Fake (Client-Side State Machine)

**Status:** Done | **Area:** Frontend / Integration | **Effort:** L | **Depends:** C05

**Evidence:**
- `frontend/src/features/host/listings/host-listing-detail-page-view.tsx`: imports `archiveListingTransition`, `submitListingTransition`, `reactivateListingTransition` từ `@/mocks/host-listings`.
- Click "Submit" → local state update → banner "Listing transitioned from DRAFT to PENDING_APPROVAL."

**Problem:** State machine duplicate backend. User tưởng submit thật.

**Fix direction:** Tạo `features/host/listings/api.ts` với mutations. Backend response = source of truth.

**Acceptance:** Submit → call backend → refetch → status updated. Mock transitions deleted.

---

## C07 — Docs/Code Drift

**Status:** Done | **Area:** Docs | **Effort:** XS | **Depends:** None

**Evidence:**
- `README.md` nói "Phase 1 Foundation" nhưng code Phase 5.
- `extracted.txt` legacy SRS vẫn trong repo.
- `fix_year.py` hardcode path `C:\Car Rentail\`.

**Fix direction:** Update README, delete legacy files, sync Java version.

**Acceptance:** README match code. Legacy files gone.

---

# IMPORTANT (Sửa trước Phase 6)

---

## I01 — Cancel Booking Chỉ Cho HELD
**Status:** Confirmed | **Evidence:** `BookingService.cancelBooking()` check `status != HELD` → throw | **Effort:** XS | **Fix:** Document limitation, expand Phase 7.

## I02 — GlobalExceptionHandler Per-Entity Not Found
**Status:** Done | **Evidence:** `GlobalExceptionHandler.java` handlers riêng cho Vehicle/Listing/BookingNotFoundException — duplicate ResourceNotFoundException | **Effort:** M | **Fix:** Collapse thành 1 generic handler.

## I03 — DataIntegrity String Matching
**Status:** Confirmed | **Evidence:** `GlobalExceptionHandler.java`: `message.contains("uq_listings_one_active_per_vehicle")` | **Effort:** S | **Fix:** SQLState/constraint check. **Depends:** I02.

## I04 — IdempotencyException Magic String Status
**Status:** Confirmed | **Evidence:** `GlobalExceptionHandler.java`: string compare code → HTTP status | **Effort:** M | **Fix:** Typed exceptions. **Depends:** I02.

## I05 — SecurityContext Interface @Component
**Status:** Done | **Evidence:** `@Component public interface SecurityContext` | **Effort:** XS | **Fix:** Remove annotation.

## I06 — Swagger Exposed Without Profile Gate
**Status:** Done | **Evidence:** `SecurityConfig.java` permitAll swagger | **Effort:** S | **Fix:** Profile-gate prod.

## I07 — CORS Credentials + Wildcard
**Status:** Done | **Evidence:** `SecurityConfig.java` allowCredentials + potential wildcard | **Effort:** S | **Fix:** Validate allowlist.

## I08 — JwtAuthenticationEntryPoint Reflection
**Status:** Done | **Evidence:** `JwtAuthenticationEntryPoint.java` `getClass().getMethod("getCode")` | **Effort:** S | **Fix:** Type-cast.

## I09 — Unauthenticated /host/* → 403 (Should 401)
**Status:** Done | **Evidence:** `JwtAuthenticationEntryPoint.java` URI prefix check → FORBIDDEN | **Effort:** S | **Fix:** 401 unauthed, 403 wrong role.

## I10 — Listing Search Native SQL + Dead Specification
**Status:** Confirmed | **Evidence:** `ListingSearchRepositoryCustomImpl.java` dual code path | **Effort:** M | **Fix:** Remove dead Specification.

## I11 — Availability Generation N+1
**Status:** Done | **Evidence:** `AvailabilityService.generateForListing()` loop 365 × `existsBy...` | **Effort:** S | **Fix:** `INSERT ... SELECT generate_series(...) ON CONFLICT DO NOTHING`.

## I12 — Vehicle Archive Loop Save
**Status:** Done | **Evidence:** `VehicleService.archiveVehicle()` loop `save(listing)` | **Effort:** XS | **Fix:** Batch `@Modifying @Query UPDATE`.

## I13 — Forgot/Reset/Change Password Missing
**Status:** Confirmed | **Evidence:** No endpoint auth/forgot-password, reset-password, me/password | **Effort:** L | **Fix:** PasswordResetToken + 3 endpoints + email stub.

## I14 — Email Verification Not Enforced
**Status:** Confirmed | **Evidence:** `AuthUser.emailVerified` field, register=false, login no check | **Effort:** L | **Fix:** Token + gated actions. **Depends:** I13.

## I15 — Register Silent-Drop ADMIN Role
**Status:** Done | **Evidence:** `AuthService.resolveRoles()` filter ADMIN, default CUSTOMER | **Effort:** XS | **Fix:** Explicit error.

## I17 — Account Lockout Missing
**Status:** Confirmed | **Evidence:** No failed attempt tracking | **Effort:** M | **Fix:** Counter + lock_until. **Depends:** C04.

## I18 — Suspended User Error Generic
**Status:** Done | **Evidence:** `AuthService.login()` throw same error for wrong pwd + suspended | **Effort:** S | **Fix:** Specific `AccountSuspendedException` (403, code `AUTH_ACCOUNT_SUSPENDED`) thrown only after password validation to avoid enumeration leakage.

## I19 — JWT Valid After Suspend
**Status:** Suspected | **Evidence:** Stateless JWT 15m, no blacklist | **Effort:** M | **Fix:** Redis blacklist or user-version.

## I22 — Phone No Pattern Validation
**Status:** Done | **Evidence:** UpdateProfileRequest has `@Size` but no `@Pattern` | **Effort:** XS | **Fix:** `@Pattern(regexp = "^\\+?[0-9\\-\\s]{7,20}$")`.

## I24 — BookingService God Method
**Status:** Confirmed | **Evidence:** `BookingService.createBooking()` ~20 steps | **Effort:** L | **Fix:** Extract Validator/Factory/Reserver. **Depends:** C01.

## I25 — Module Boundary Violations
**Status:** Confirmed | **Evidence:** AuthService → user module; VehicleService → booking/listing repos; AdminListingService → AvailabilityService (service-to-service) | **Effort:** L | **Fix:** Domain events, ports/interfaces.

## I26 — Entity Relationship 3 Styles
**Status:** Confirmed/Suspected | **Evidence:** Listing=ManyToOne, Booking=UUID-only, Availability=composite | **Effort:** XL | **Fix:** Choose 1 pattern, document, migrate.

## I27 — DTO Mapping 5 Ways
**Status:** Confirmed/Suspected | **Evidence:** VehicleMapper, ListingResponse.from, BookingService.toResponse, RegisterResponse.from, ExtraResponse.from | **Effort:** L | **Fix:** Pick 1 convention.

## I28 — Pagination Response Inconsistent
**Status:** Suspected | **Evidence:** Booking=PageResponse, others may be Spring Page | **Effort:** M | **Fix:** Normalize PageResponse.

## I30 — OptimisticLock No Handler
**Status:** Done | **Evidence:** @Version on entities, no handler in GlobalExceptionHandler | **Effort:** S | **Fix:** Handler → 409.

## I31 — Idempotency Cleanup Job Missing
**Status:** Spec-only/Suspected | **Evidence:** `expires_at` field, no scheduler | **Effort:** S | **Fix:** Scheduled DELETE.

## I32 — HELD Expiry Scheduler Verify
**Status:** Suspected | **Evidence:** Roadmap requires, need verify code | **Effort:** M | **Fix:** Verify or implement.

## I34 — Frontend API Client Singleton
**Status:** Confirmed | **Evidence:** `api-client.ts` module-level mutable `let accessTokenGetter` | **Effort:** M | **Fix:** Context-based or test reset.

## I35 — BFF vs Direct API Undocumented
**Status:** Confirmed | **Evidence:** Auth via BFF, others via rewrite | **Effort:** XS | **Fix:** Document.

## I36 — Middleware No Role Check
**Status:** Confirmed | **Evidence:** `middleware.ts`: `if (refreshCookie) next()` | **Effort:** M | **Fix:** Read role from cookie.

## I37 — RoleGuard Flash Content
**Status:** Confirmed | **Evidence:** `role-guard.tsx` useEffect redirect | **Effort:** M | **Fix:** Layout-based protection. **Depends:** I36.

## I38 — Mobile Nav Missing
**Status:** Confirmed | **Evidence:** `app-shell.tsx`: `hidden md:flex` on nav, no mobile replacement | **Effort:** M | **Fix:** Hamburger + Sheet.

## I39 — UI Mix Vietnamese/English
**Status:** Confirmed | **Evidence:** Auth=VN, listings/host=EN | **Effort:** S | **Fix:** VN primary.

## I40 — Form Handling 3 Patterns
**Status:** Confirmed | **Evidence:** Auth=Zod+RHF, host/booking=useState+manual, filter=no schema | **Effort:** L | **Fix:** Standardize Zod+RHF.

## I41 — Error Handling Per-Page
**Status:** Confirmed | **Evidence:** `booking-create-page-view.tsx` if-else chain by error code | **Effort:** M | **Fix:** Central handler. **Depends:** I40.

## I42 — Date Timezone Risk
**Status:** Suspected | **Evidence:** `Date.parse(\`${date}T00:00:00\`)` in booking-create | **Effort:** S | **Fix:** String compare or date-fns.

## I43 — Loading/Empty/Error Inconsistent
**Status:** Confirmed | **Evidence:** 3+ loading styles, 3+ error styles | **Effort:** M | **Fix:** Shared components.

## I44 — Pay Now Exposes Phase 6 Language
**Status:** Suspected | **Evidence:** `PAY_NOW_TOOLTIP = "...Phase 6 (sắp ra mắt)"` | **Effort:** XS | **Fix:** Remove phase mention.

## I46 — No AbortController
**Status:** Confirmed | **Evidence:** `api-client.ts` no signal param | **Effort:** S | **Fix:** Add AbortSignal.

## I47 — Mocks Global Instead of Feature-local
**Status:** Confirmed | **Evidence:** `src/mocks/*.ts` imported across features | **Effort:** M | **Fix:** Move to `__mocks__/` or env adapter. **Depends:** C05, C06.

---

# NICE TO IMPROVE (Defer)

| ID | Issue | Status | Note |
|---|---|---|---|
| N01 | JWT key rotation | Spec-only | Sau auth baseline |
| N02 | MFA/2FA | Spec-only | Scope creep |
| N03 | OAuth/social login | Spec-only | Sau auth baseline |
| N04 | Full i18n | Spec-only | Sau VN consistent |
| N05 | Storybook | Spec-only | Sau shared components |
| N06 | Kafka | Spec-only | Sau outbox |
| N07 | Microservices | Spec-only | Overengineering |
| N08 | Event sourcing | Spec-only | Outbox đủ |
| N09 | Real payment gateway | Spec-only | Stub đủ |
| N10 | Payout automation | Spec-only | Sau payment |
| N11 | Analytics dashboard | Spec-only | P2 |
| N12 | MinIO photos | Spec-only | Phase 9 |
| N13 | Trip lifecycle | Spec-only | Phase 9 |
| N14 | Reviews/disputes | Spec-only | Phase 9 |

---

# Summary

| Category | Count | Effort |
|---|---|---|
| Critical (C01-C07) | 7 | ~40-60h |
| Important Backend (I01-I32) | ~20 | ~80-120h |
| Important Frontend (I34-I47) | ~12 | ~60-80h |
| Nice (N01-N14) | 14 | Defer |
| **Total actionable** | **~39** | **~180-260h** |
