# Phase 02 — Auth + User

## Goal

Implement authentication (register, login, refresh, logout with JWT) and basic user profile management.

## Must Implement

- [x] BCrypt password hashing (strength 12)
- [x] `AuthUser` entity + repository
- [x] `UserRole` entity + repository (enum: CUSTOMER, HOST, ADMIN)
- [x] `RefreshToken` entity + repository with token hash storage
- [x] JWT access token generation and validation (15-minute expiry)
- [x] Refresh token generation (7-day expiry, rotation)
- [x] `JwtTokenProvider` service
- [x] `JwtAuthenticationFilter`
- [x] `JwtAuthenticationEntryPoint` (returns 401 JSON, never HTML redirect)
- [x] `SecurityConfig`: stateless session, CSRF disabled, public endpoints for `/auth/**`, `/listings` (GET), `/listings/{id}/availability` (GET)
- [x] `POST /api/v1/auth/register`: validate email unique, hash password, create user with CUSTOMER role, return access token
- [x] `POST /api/v1/auth/login`: verify credentials, generate tokens, update `last_login_at`
- [x] `POST /api/v1/auth/refresh`: validate refresh token, rotate (revoke old, issue new)
- [x] `POST /api/v1/auth/logout`: revoke refresh token (set `revoked_at`)
- [x] `UserProfile` entity + repository
- [x] `GET /api/v1/users/me`: return profile (sensitive fields excluded)
- [x] `PATCH /api/v1/users/me`: update fullName, phone, dateOfBirth, addressLine
- [x] `UserService` with resource-level authorization helper
- [x] RBAC: CUSTOMER, HOST, ADMIN roles through `user_roles` table
- [x] `GET /api/v1/admin/users?status=&role=&page=&size=`

## Must Not Implement

- [ ] Driver verification
- [ ] Payment
- [ ] Booking
- [ ] Vehicle/listing
- [ ] Rate limiting
- [ ] Refresh token stored in Redis (use DB for now)

## Files/Modules Expected

```
com.rentflow.auth/
├── controller/
│   └── AuthController.java
├── service/
│   ├── AuthService.java
│   └── JwtTokenProvider.java
├── entity/
│   ├── AuthUser.java
│   ├── UserRole.java
│   └── RefreshToken.java
├── repository/
│   ├── AuthUserRepository.java
│   ├── UserRoleRepository.java
│   └── RefreshTokenRepository.java
└── dto/
    ├── RegisterRequest.java
    ├── LoginRequest.java
    ├── AuthResponse.java
    └── RefreshRequest.java

com.rentflow.user/
├── controller/
│   ├── UserController.java
│   └── AdminUserController.java
├── service/
│   └── UserService.java
├── entity/
│   └── UserProfile.java
├── repository/
│   └── UserProfileRepository.java
└── dto/
    ├── UserProfileResponse.java
    └── UpdateProfileRequest.java

com.rentflow.common.security/
├── JwtAuthenticationFilter.java
├── JwtAuthenticationEntryPoint.java
└── SecurityConfig.java
```

## API Contracts

### POST /api/v1/auth/register

Request:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "fullName": "Nguyen Van A"
}
```

Response: `201 Created`
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 900,
  "tokenType": "Bearer",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "roles": ["CUSTOMER"]
  }
}
```

### POST /api/v1/auth/login

Request:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

Response: `200 OK` (same as register response)

### POST /api/v1/auth/refresh

Request:
```json
{
  "refreshToken": "eyJ..."
}
```

Response: `200 OK` (new access token + refresh token)

### POST /api/v1/auth/logout

Request:
```json
{
  "refreshToken": "eyJ..."
}
```

Response: `204 No Content`

### GET /api/v1/users/me

Response: `200 OK`
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "roles": ["CUSTOMER"],
  "fullName": "Nguyen Van A",
  "phone": "0900000000",
  "dateOfBirth": "1999-01-01",
  "addressLine": "Hanoi",
  "driverVerificationStatus": "NOT_SUBMITTED"
}
```

## Acceptance Criteria

- [x] Unauthenticated request to protected endpoint returns `401` with JSON body
- [x] User with wrong role accessing admin endpoint returns `403`
- [x] Register with existing email returns `409 USER_EMAIL_EXISTS`
- [x] Login with wrong password returns `401 AUTH_INVALID_CREDENTIALS`
- [x] Refresh token rotation works: old token revoked, new issued
- [x] Expired access token returns `401 AUTH_TOKEN_EXPIRED`
- [x] User profile update persists correctly
- [x] ADMIN role assignment is NOT exposed through public API in P0

## Tests Required

- [x] Unit: BCrypt hash and verify
- [x] Unit: JWT generate and validate
- [x] Unit: Refresh token rotation logic
- [x] Integration: register success
- [x] Integration: register duplicate email -> 409
- [x] Integration: login success
- [x] Integration: login wrong credentials -> 401
- [x] Integration: access protected endpoint without token -> 401
- [x] Integration: access with invalid token -> 401
- [x] Integration: refresh token rotation
- [x] Integration: logout revokes token
- [x] Security: JWT tampering -> 401

## Notes

- JWT secret from environment variable `JWT_SECRET`.
- Refresh token stored in DB as hash (SHA-256).
- ADMIN role assigned directly in DB for demo accounts, not via API.
- All dates use ISO-8601 format.
- Timezone: store as TIMESTAMPTZ, interpret as UTC.
