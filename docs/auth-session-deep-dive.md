# Auth + Session — Deep-Dive Review

**Date:** 2026-06-03 | **Scope:** Frontend BFF → Backend Controller → Service → Repository → Response → Frontend Rendering

---

## Verdict

**PARTIAL — Solid foundation with 3 critical backend gaps mitigated by a well-built frontend BFF**

The auth system has production-grade building blocks: BCrypt(12) hashing, constant-time login, refresh token rotation with family-revocation on reuse, account lockout (3 attempts / 15 min), rate limiting (Redis sorted-set), and JWT HS512. The frontend BFF adds an httpOnly cookie security layer. However, three capabilities are missing from the backend, and the backend-only path exposes refresh tokens in JSON — safe only as long as the BFF is always used.

---

## Flow Map

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              AUTH FLOW — FULL STACK                              │
├─────────────┬──────────────────────┬──────────────────────┬──────────────────────┤
│  Browser    │  Next.js BFF         │  Spring Boot         │  PostgreSQL / Redis  │
├─────────────┼──────────────────────┼──────────────────────┼──────────────────────┤
│             │                      │                      │                      │
│  Login Page │──POST /api/auth/login──▶ callBackend() ────▶ POST /auth/login     │
│  (client    │  (Route Handler)     │  AuthController      │  AuthService         │
│   fetch)    │                      │                      │    │                 │
│             │◀─ sets httpOnly ────│◀─ TokenResponse ────│    ├─ find user      │
│             │   cookies            │   {accessToken,      │    ├─ BCrypt check   │
│             │                      │    refreshToken,     │    ├─ lockout check  │
│             │◀─ SessionPayload ────│    user}             │    ├─ gen JWT        │
│             │   {accessToken,      │                      │    ├─ create refresh │
│             │    expiresAt,        │                      │    └─ log           │
│             │    user}             │                      │                      │
│             │   (NO refreshToken!) │                      │                      │
│             │                      │                      │                      │
│  AuthProvider                      │                      │                      │
│  ├─ accessTokenRef = token         │                      │                      │
│  ├─ user = session.user            │                      │                      │
│  └─ status = "authenticated"       │                      │                      │
│             │                      │                      │                      │
│  Page load  │──GET /api/auth/session──▶ POST /auth/refresh│                      │
│  (auth-     │  (silent rehydrate)  │  + GET /users/me     │  RefreshTokenService │
│   context)  │                      │                      │    ├─ rotate token   │
│             │◀─ SessionPayload ────│                      │    ├─ revoke old     │
│             │                      │                      │    └─ reuse detect   │
│             │                      │                      │                      │
│  Any API    │──GET /api/v1/xxx────▶──────────────────────▶ JwtAuthFilter        │
│  call       │  Authorization:      │                      │  ├─ validate JWT    │
│  (api-      │  Bearer <access>     │                      │  ├─ check status    │
│   client)   │                      │                      │  └─ set principal   │
│             │  On 401:             │                      │                      │
│             │──POST /api/auth/refresh──▶ POST /auth/refresh│                     │
│             │  (auto-retry once)   │                      │                      │
│             │                      │                      │                      │
│  Proxy.ts   │  Reads cookies       │                      │                      │
│  (edge)     │  rentflow_refresh    │                      │                      │
│             │  rentflow_role       │                      │                      │
│             │  → /login, /forbidden│                      │                      │
│             │  or pass through     │                      │                      │
└─────────────┴──────────────────────┴──────────────────────┴──────────────────────┘
```

---

## Findings

### F1 — 🔴 BLOCKER: No change-password endpoint wired

**Files:** `AuthController.java`, `PasswordService.java:84-95`

`PasswordService.changePassword(userId, currentPassword, newPassword)` is fully implemented — BCrypt verification of old password, BCrypt encoding of new password, save, and revoke all refresh tokens. But no controller endpoint calls it.

**How to reproduce:** Attempt `POST /api/v1/auth/change-password` with valid credentials → 404 or 401 (not in SecurityConfig either).

**Why it matters:** Users cannot change their password while logged in. Only the forgot-password flow works (requires email access). This is a basic auth requirement.

**Recommended fix:** 
1. Add to `AuthController`: `@PostMapping("/change-password")` calling `passwordService.changePassword(currentUserId, request.currentPassword(), request.newPassword())`
2. Add to `SecurityConfig`: `.requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password").authenticated()`
3. The `ChangePasswordRequest` DTO already exists at `auth/dto/ChangePasswordRequest.java`

**Test to add:** `POST /api/v1/auth/change-password` with valid old password → 204. With wrong old password → 401. With new password same as old → 400.

---

### F2 — 🔴 BLOCKER: No resend-verification-email endpoint

**Files:** `AuthController.java`, `EmailVerificationService.java`

`EmailVerificationService.sendVerificationBestEffort()` exists but is only called at registration. After registration, there is no way to request a new verification email.

**How to reproduce:** Register user, don't verify, try to request new verification → no endpoint exists.

**Why it matters:** Users who lose the verification email are permanently locked out of verification if the feature becomes enforced at login.

**Recommended fix:** Add `POST /api/v1/auth/verify-email/resend` (authenticated) calling `emailVerificationService.sendVerificationBestEffort(currentUserId)`.

**Test to add:** Resend creates new token, old token still works until its own expiry.

---

### F3 — 🔒 HIGH: Email verification not enforced at login

**Files:** `AuthService.java:83-126`

The `login()` method checks: user exists, account not locked, password matches, status is ACTIVE. It does NOT check `user.getEmailVerified()`. An unverified user can log in and get full access.

**Evidence:** `AuthService.java` line 83-126 — no mention of `emailVerified` field.

**Why it matters:** Email verification is cosmetic — it marks the user as verified but never gates access. If verification was intended as a security measure, it is completely ineffective.

**Recommended fix:** After status check (line 105-107), add:
```java
if (!user.getEmailVerified()) {
    throw new EmailNotVerifiedException();  // maps to 403 in GlobalExceptionHandler
}
```
Or make it configurable via a property `auth.require-email-verification` that defaults to `false` for backward compatibility.

**Test to add:** `AuthIntegrationTest`: register → login succeeds (unverified allowed with flag off). With flag on → login returns 403 `EMAIL_NOT_VERIFIED`.

---

### F4 — 🔒 HIGH: Backend refreshToken exposed in JSON body

**Files:** `TokenResponse.java:10`, `TokenOnlyResponse.java:10`, `AuthController.java:43,52`

The backend returns `refreshToken` as a plain string in the JSON response body. Any code that calls the backend login endpoint directly (bypassing the BFF) receives the raw refresh token in JavaScript.

**Contrast with frontend BFF:** The frontend BFF (`/api/auth/login/route.ts`) CORRECTLY strips the refreshToken from the response and stores it as an httpOnly cookie. `SessionPayload` contains only `accessToken`, `accessTokenExpiresAt`, and `user`. The refresh token never reaches browser JS.

**Why it matters:** This is safe AS LONG AS all clients go through the BFF. However:
- Swagger/OpenAPI users testing the backend directly will see raw refresh tokens
- If a mobile app or third-party client connects directly, refresh tokens leak
- The backend itself has no protection — it's always optional

**Recommended fix:** Add a `spring.profiles.active` check — in `prod` profile, strip refreshToken from TokenResponse and set it as a `Set-Cookie` header with `httpOnly; Secure; SameSite=Strict` from the backend. Or document clearly that direct backend access should never be used by browser clients.

**Test to add:** Verify that in prod profile, `POST /api/v1/auth/login` response body does NOT contain `refreshToken` but does set `Set-Cookie` header.

---

### F5 — 🟡 MEDIUM: TokenResponse vs TokenOnlyResponse shape mismatch

**Files:** `TokenResponse.java`, `TokenOnlyResponse.java`, `AuthController.java:43,52`

`/login` returns `TokenResponse` (includes `user` profile).
`/refresh` returns `TokenOnlyResponse` (no `user` profile).

The frontend BFF handles this correctly:
- Login BFF returns full `SessionPayload` (user + tokens)
- Refresh BFF returns only `{ accessToken, accessTokenExpiresAt }`
- Session BFF returns full `SessionPayload` (user + tokens) after refresh + `/users/me`

**Why it matters:** The backend contract is inconsistent. A client calling `/refresh` directly won't get the user profile. Harmony would be better: either always include user, or always exclude user and have clients call `/users/me` separately.

**Recommended fix:** Unify to one response type. Simpler: always include `user` in both login and refresh responses (update `TokenOnlyResponse` to extend `TokenResponse` or merge them).

**No immediate action required** since the BFF handles both correctly. This is a code quality concern.

---

### F6 — 🟡 MEDIUM: No "logout everywhere" capability

**Files:** `RefreshTokenService.java:93-102`, `AuthService.java:155-158`

`logout()` revokes only the specific refresh token sent in the request. Other devices/tabs with different refresh tokens remain authenticated. There is no `POST /api/v1/auth/logout-all` endpoint.

`PasswordService` DOES revoke all tokens on password change (line 93: `refreshTokenRepository.revokeAllByUserId()`), but there's no user-facing way to do this.

**Why it matters:** A user who logs in from a shared computer cannot remotely terminate all sessions.

**Recommended fix:** Add `POST /api/v1/auth/logout-all` (authenticated) calling `refreshTokenRepository.revokeAllByUserId(currentUserId, now)`.

**Test to add:** Login from two devices → logout-all from one → both devices get 401 on refresh.

---

### F7 — 🟡 MEDIUM: `secure` flag conditionally set in frontend

**File:** `frontend/src/lib/server/session-cookie.ts:12`

```typescript
secure: process.env.NODE_ENV === "production",
```

In local dev, cookies are sent over HTTP (correct for localhost). But if a staging environment has `NODE_ENV=development`, cookies are not `secure`.

**Why it matters:** If `NODE_ENV` is misconfigured in a staging deploy, refresh tokens travel over HTTP — interceptable on shared networks.

**Recommended fix:** Use an explicit env var `COOKIE_SECURE=true` instead of checking `NODE_ENV`. Default to `true` in all non-local environments.

**Test to add:** Session cookie test: with `COOKIE_SECURE=false` → cookie has no Secure flag. With `COOKIE_SECURE=true` → cookie has Secure flag.

---

### F8 — ✅ LOW / NOTE: Frontend BFF is excellent mitigation

The frontend BFF pattern is well-executed:
- httpOnly cookies for refresh token + role (JS cannot steal them)
- Access token in React ref (lost on page reload, refreshed via `/api/auth/session`)
- Session rehydration on every page load via silent refresh
- 401 auto-retry with deduplication via `refreshInFlightRef`
- Two-layer route protection: edge proxy (cookies) + server layouts (optional backend check)
- No localStorage usage for tokens
- 160 frontend tests covering auth

The only improvement would be to move the access token to an httpOnly cookie as well, making the frontend fully CSRF-only (requires SameSite=Strict + anti-CSRF token for mutations). This is a P2 enhancement.

---

## Contract Mismatches

| Area | Backend | Frontend BFF | Status |
|------|---------|-------------|--------|
| Login response | `TokenResponse` (accessToken + refreshToken + user) | Strips refreshToken → `SessionPayload` (accessToken + user) | OK — BFF adds security |
| Refresh response | `TokenOnlyResponse` (tokens only, no user) | Returns `{ accessToken, accessTokenExpiresAt }` | OK — shape compatible |
| Refresh method | POST with JSON body `{ refreshToken }` | POST with JSON body `{ refreshToken }` | MATCH |
| Logout method | POST with JSON body `{ refreshToken }` | POST with JSON body `{ refreshToken }` | MATCH |
| Register roles | `roles` field optional, defaults to `["CUSTOMER"]` | `roles` field sent as array | MATCH |
| 401 response | `ErrorResponse` JSON | Parsed as `ApiError` | MATCH |
| 403 response | `ErrorResponse` JSON | Parsed as `ApiError` | MATCH |
| **Change-password** | **No endpoint** | **No BFF route** | ❌ MISSING — both sides |
| **Resend-verification** | **No endpoint** | **BFF route exists** (`/api/v1/users/me/resend-verification`) | ⚠️ MISMATCH — frontend has BFF route but backend may not have the endpoint called |

---

## Security/Permission Issues

| Issue | Severity | Detail |
|-------|----------|--------|
| Refresh token in backend JSON body | HIGH | If Swagger or direct API call used, raw refresh token exposed. Mitigated by BFF. |
| Email verification not enforced | HIGH | Unverified users get full access |
| No password change endpoint | BLOCKER | Basic auth capability missing |
| No logout-all endpoint | MEDIUM | Cannot terminate all sessions |
| Frontend cookie `secure` depends on NODE_ENV | MEDIUM | Misconfigured staging leaks cookies over HTTP |
| `requireDriverVerification` gate unclear | MEDIUM | If default is false, unverified drivers can book (auth-adjacent) |
| Rate limit on login but not on register | LOW | Register could be abused for email spam |
| Refresh token rotation with reuse detection | EXCELLENT | Family revocation on replay — industry best practice |
| Account lockout below rate limit threshold | EXCELLENT | 3 failures lock before 5-attempt rate limit — defense in depth |
| Suspended user gets 401 on wrong password | EXCELLENT | No user enumeration — same error as invalid credentials |

---

## Missing Tests

| # | Test Scenario | Priority | Layer |
|---|---|---|---|
| 1 | Change-password with valid old password → 204 | P0 | Integration |
| 2 | Change-password with wrong old password → 401 | P0 | Integration |
| 3 | Resend-verification creates new token | P0 | Integration |
| 4 | Login blocked when email not verified (with flag on) | P1 | Integration |
| 5 | CORS preflight OPTIONS returns correct headers | P1 | SpringBootTest |
| 6 | No JSESSIONID in response (stateless verified) | P1 | SpringBootTest |
| 7 | CSRF disabled — POST without _csrf token works | P1 | SpringBootTest |
| 8 | Refresh with expired refresh token → 401 | P1 | Integration |
| 9 | Login response contract: all TokenResponse fields present | P2 | Integration |
| 10 | Register with weak password → 400 | P2 | Unit |
| 11 | Refresh token not in prod login response body | P2 | SpringBootTest |
| 12 | Logout-all revokes all user tokens | P2 | Integration |
| 13 | CorrelationId appears in 403 responses | P2 | SpringBootTest |

---

## Recommended Fix Slice

### Slice 1 — Wire missing endpoints (P0, ~2 hours)

**Goal:** Unblock change-password and resend-verification

**Tasks:**
1. Add `POST /api/v1/auth/change-password` to `AuthController` — delegate to `PasswordService.changePassword()`
2. Add to `SecurityConfig`: `.requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password").authenticated()`
3. Verify frontend BFF resend-verification route actually hits a working backend endpoint (the frontend agent found a BFF route at `/api/v1/users/me/resend-verification` — verify this exists on backend)
4. If backend resend-verification endpoint doesn't exist, wire `EmailVerificationService.sendVerificationBestEffort()` to a new `@PostMapping("/verify-email/resend")` in `AuthController`

**Acceptance:**
- `curl -X POST http://localhost:8087/api/v1/auth/change-password -H "Authorization: Bearer $TOKEN" -d '{"currentPassword":"old","newPassword":"NewPass123!"}'` → 204
- `curl -X POST ... -d '{"currentPassword":"wrong","newPassword":"NewPass123!"}'` → 401
- `curl -X POST http://localhost:8087/api/v1/auth/verify-email/resend -H "Authorization: Bearer $TOKEN"` → 204

**Tests:** `AuthIntegrationTest` — changePasswordSuccess, changePasswordWrongCurrent, resendVerificationSuccess

---

### Slice 2 — Email verification gate (P1, ~1 hour)

**Goal:** Make verification meaningful

**Tasks:**
1. Add `rentflow.auth.require-email-verification` property (default `false` for backward compat)
2. Add check in `AuthService.login()` after status check
3. Toggle `require-email-verification=true` in `application-prod.yml`
4. Update `EmailVerificationIntegrationTest` to test the login gate

**Acceptance:**
- With flag `true`: unverified login → 403 `EMAIL_NOT_VERIFIED`
- With flag `false`: unverified login → 200 (current behavior)

---

### Slice 3 — Backend cookie path (P2, ~2 hours)

**Goal:** Eliminate refresh token from JSON body entirely

**Tasks:**
1. In `TokenResponse`/`TokenOnlyResponse`, annotate `refreshToken` with `@JsonIgnore` in prod profile
2. Add `Set-Cookie` header in `AuthController.login()` and `AuthController.refresh()` responses
3. Cookie attributes: `httpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800`
4. Update `AuthController.logout()` to clear the cookie
5. Update `RefreshRequest` and `LogoutRequest` to read from cookie instead of body (or keep both with cookie taking precedence)

**Acceptance:** Prod profile responses have no `refreshToken` field. Cookie is set and read correctly.

---

## Commands to Run

```powershell
# All auth-related tests
.\mvnw.cmd test -Dtest="com.rentflow.auth.*,com.rentflow.common.security.*,com.rentflow.integration.auth.*,com.rentflow.integration.SecurityIntegrationTest"

# Fastest feedback — security endpoint tests
.\mvnw.cmd test -Dtest="com.rentflow.common.security.SecurityEndpointsTest"

# Integration tests with Testcontainers
.\mvnw.cmd verify -Pintegration-tests -Dtest="AuthIntegrationTest,PasswordResetIntegrationTest,EmailVerificationIntegrationTest"

# Frontend auth tests
cd frontend; pnpm test -- --reporter=verbose src/features/auth/ src/app/api/auth/ src/lib/api-client.test.ts src/lib/server/session-cookie.test.ts src/proxy.test.ts

# Backend run
.\mvnw.cmd spring-boot:run

# Frontend run
cd frontend; pnpm dev
```
