# Phase 08A — Driver Verification

## Goal

Implement driver verification: license submission, admin review, automatic expiry, and booking gate.

## Must Implement

- [ ] `DriverVerification` entity + repository
- [ ] `DriverVerificationService`: submit, approve, reject, expire
- [ ] License number encryption (AES) and hash (SHA-256) for duplicate lookup
- [ ] Duplicate guard: at most one PENDING or APPROVED verification per customer
- [ ] Re-submission allowed only after REJECTED or EXPIRED
- [ ] `POST /api/v1/users/me/driver-license` — submit license
- [ ] `GET /api/v1/admin/driver-verifications?status=&page=&size=` — list verifications
- [ ] `POST /api/v1/admin/driver-verifications/{id}/approve` — approve with reason
- [ ] `POST /api/v1/admin/driver-verifications/{id}/reject` — reject with reason
- [ ] Daily expiry job: APPROVED/PENDING -> EXPIRED when license_expiry_date < current_date
- [ ] Booking gate: customer must have APPROVED driver verification before creating booking
- [ ] Feature flag: `rentflow.booking.require-driver-verification=false` disables gate
- [ ] Sensitive-data filtering: host never sees customer license number, license document, phone, address

## Must Not Implement

- [ ] Rate limiting (Phase 8B)
- [ ] Notifications (Phase 8B)
- [ ] Host approval/rejection flow (Phase 8B)
- [ ] Host approval expiry job (Phase 8B)
- [ ] MinIO file upload (P9)
- [ ] Audit logging (P9)
- [ ] Outbox (P9)

## Files/Modules Expected

```
com.rentflow.user/
├── service/
│   └── DriverVerificationService.java
├── entity/
│   └── DriverVerification.java
├── repository/
│   └── DriverVerificationRepository.java
└── dto/
    ├── SubmitDriverLicenseRequest.java
    └── DriverVerificationResponse.java

com.rentflow.scheduler/
└── ExpireDriverVerificationsJob.java
```

## API Contracts

### POST /api/v1/users/me/driver-license

Request:
```json
{
  "licenseNumber": "0123456789",
  "licenseExpiryDate": "2028-05-09",
  "documentFileId": "uuid"
}
```

Response: `201 Created`
```json
{
  "id": "uuid",
  "status": "PENDING",
  "licenseExpiryDate": "2028-05-09",
  "submittedAt": "2026-05-09T12:00:00Z"
}
```

### GET /api/v1/admin/driver-verifications

Query: `?status=PENDING&page=0&size=20`

### POST /api/v1/admin/driver-verifications/{id}/approve

Request:
```json
{
  "reason": "License verified, valid until 2028"
}
```

### POST /api/v1/admin/driver-verifications/{id}/reject

Request:
```json
{
  "reason": "License number does not match records"
}
```

## Driver Verification Rules

| Rule | Description |
|---|---|
| Duplicate guard | At most one PENDING or APPROVED per customer |
| Re-submission | Allowed only after REJECTED or EXPIRED |
| Expiry | APPROVED/PENDING expires when license_expiry_date < current_date |
| Expiry job | Daily at 00:00 UTC |
| Booking gate | APPROVED required before booking (dev flag can disable) |
| Existing bookings | Expiry does not cancel existing bookings |
| Sensitive data | Host never sees license number, document, address, phone |

## TX-08A: Driver Verification Expiry Job

```
1. Find driver_verifications where status IN ('PENDING', 'APPROVED')
   and license_expiry_date < current_date.
2. Process in batches of 100 with FOR UPDATE SKIP LOCKED.
3. For each row:
   - lock row FOR UPDATE
   - update driver_verifications.status = EXPIRED
   - update user_profiles.driver_verification_status = EXPIRED
4. Existing bookings remain unchanged.
```

## Acceptance Criteria

- [ ] Customer can submit driver verification
- [ ] Duplicate PENDING/APPROVED submission returns 409 ALREADY_SUBMITTED
- [ ] Re-submission allowed after REJECTED or EXPIRED
- [ ] Admin can approve/reject verification with reason
- [ ] Expired license blocks new booking creation
- [ ] Existing bookings remain valid after license expiry
- [ ] Daily expiry job updates expired verifications
- [ ] Host cannot see customer license number in booking detail
- [ ] Host cannot see customer phone, address in booking detail

## Tests Required

- [ ] Unit: Duplicate pending verification rejected
- [ ] Unit: Re-submission after REJECTED allowed
- [ ] Unit: Re-submission after EXPIRED allowed
- [ ] Unit: Expired license blocks booking
- [ ] Integration: Submit verification -> PENDING
- [ ] Integration: Duplicate submission -> 409
- [ ] Integration: Admin approve -> APPROVED
- [ ] Integration: Admin reject -> REJECTED
- [ ] Integration: Expiry job updates expired verifications
- [ ] Integration: Expired driver cannot create booking -> 403
- [ ] Integration: Host cannot see sensitive customer data

## Notes

- For P0, driver verification gate can be disabled with feature flag.
- License number must be encrypted at rest. Use AES-256 with key from environment.
- License number hash used for duplicate detection.
