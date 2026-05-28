# Phase 08B — Hardening, Notifications, Rate Limiting

## Goal

Implement hardening features: rate limiting, notifications, host approval/rejection flow, and void retry admin notification.

## Slice Status (2026-05-29)

- [x] Slice 8B.2 — Public endpoint rate-limit contract hardening
  - Added WebMvc contract coverage for:
    - `GET /api/v1/listings`
    - `GET /api/v1/listings/{id}`
    - `GET /api/v1/listings/{id}/availability`
    - `GET /api/v1/health`
    - `GET /actuator/health`
  - Verified contract on limit exceed:
    - HTTP `429`
    - `Retry-After` header
    - JSON error body fields: `code`, `message`, `correlationId`
  - Added tests for path normalization edge cases (trailing slash, context path) and default property values (`login=15m`, `booking=1h`, `public=60/1m`).

## Must Implement

### Rate Limiting (Redis)

- [ ] Rate limiting service using Redis
- [ ] `RateLimitService`: checkAndIncrement(key, limit, windowSeconds)
- [ ] `RateLimitFilter`: applies to mutation endpoints
- [ ] Login rate limit: 5 attempts / 15 min / IP or email
- [ ] Booking rate limit: 10 attempts / hour / user
- [ ] Public endpoint rate limit: 60 requests / min / IP
- [ ] 429 TOO_MANY_REQUESTS response with `Retry-After` header

### Notifications

- [ ] `Notification` entity + repository
- [ ] `NotificationService`: create notification
- [ ] `NotificationController` at `/api/v1/notifications/me?page=&size=`
- [ ] Notification delivery_status: PENDING, SENT, FAILED

### Host Approval Flow

- [ ] `GET /api/v1/host/bookings?status=&page=&size=` — host's booking requests
- [ ] `POST /api/v1/host/bookings/{id}/approve`
- [ ] `POST /api/v1/host/bookings/{id}/reject`
- [ ] Idempotency scopes: HOST_APPROVE_BOOKING, HOST_REJECT_BOOKING
- [ ] Host approval: PENDING_HOST_APPROVAL -> CONFIRMED, availability HOLD -> BOOKED
- [ ] Host rejection: PENDING_HOST_APPROVAL -> REJECTED, VOID authorization, availability -> FREE
- [ ] Host approval expiry job: PENDING_HOST_APPROVAL -> EXPIRED after 24 hours
- [ ] Void retry admin notification: when cancellation with partial penalty fails VOID, alert admin

## Must Not Implement

- [ ] Real file upload to MinIO (P9)
- [ ] Trip check-in/check-out (P9)
- [ ] Reviews (P9)
- [ ] Disputes (P9)
- [ ] Reports (P9)
- [ ] Payouts (P9)
- [ ] Kafka integration (P9)
- [ ] Audit logging (P9)
- [ ] Outbox scheduler / event publishing (P9)

## Files/Modules Expected

```
com.rentflow.common.security/
├── rate/
│   ├── RateLimitService.java
│   └── RateLimitFilter.java
└── authorization/
    ├── BookingAuthorizationHelper.java
    └── ResourceOwnershipValidator.java

com.rentflow.notification/
├── controller/
│   └── NotificationController.java
├── service/
│   └── NotificationService.java
├── entity/
│   └── Notification.java
└── repository/
    └── NotificationRepository.java

com.rentflow.scheduler/
└── ExpireHostApprovalJob.java
```

## API Contracts

### GET /api/v1/notifications/me

Query: `?page=0&size=20`

Response:
```json
{
  "content": [
    {
      "id": "uuid",
      "type": "DRIVER_VERIFICATION_EXPIRED",
      "title": "Driver License Expired",
      "message": "Your driver license has expired. Please update your verification.",
      "readAt": null,
      "createdAt": "2026-05-09T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Rate Limiting

| Endpoint | Limit |
|---|---|
| Login | 5 failed attempts / 15 min / IP or email |
| Booking creation | 10 attempts / hour / user |
| Public endpoints | 60 requests / min / IP |

Response: `429 TOO_MANY_REQUESTS` with `Retry-After: seconds`

## Host Approval Flow

```
PENDING_HOST_APPROVAL booking:
  - expires after 24 hours (host_approval_expires_at)
  - Host approves -> CONFIRMED, availability HOLD -> BOOKED
  - Host rejects -> REJECTED, VOID auth, availability -> FREE
  - Timeout -> EXPIRED, VOID auth, availability -> FREE
```

## Acceptance Criteria

- [ ] Login rate limiting works (5/15min)
- [ ] Booking rate limiting works (10/hour)
- [ ] Public endpoint rate limiting works (60/min)
- [ ] 429 response includes `Retry-After` header
- [ ] Notifications created for important events
- [ ] User can view own notifications
- [ ] Host can view bookings for own listings
- [ ] Host can approve own PENDING_HOST_APPROVAL bookings
- [ ] Host can reject own PENDING_HOST_APPROVAL bookings
- [ ] Host approval expiry job works (24-hour window)
- [ ] Void retry notification sent to admin

## Tests Required

- [ ] Integration: Login rate limit exceeded -> 429
- [ ] Integration: Booking rate limit exceeded -> 429
- [ ] Integration: Host approval -> CONFIRMED
- [ ] Integration: Host rejection -> REJECTED, VOID
- [ ] Integration: Host approval timeout -> EXPIRED
- [ ] Integration: Void retry admin notification created

## Notes

- Rate limiting uses Redis INCR with TTL.
- Host approval/rejection are idempotent operations.
- Void retry notification is a notification to admin when cancellation with partial penalty fails VOID (from Phase 7).
