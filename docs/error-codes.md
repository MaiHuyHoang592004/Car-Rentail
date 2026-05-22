# Standard Error Codes — RentFlow

## Error Response Format

```json
{
  "code": "LISTING_NOT_AVAILABLE",
  "message": "Listing is not available for the selected date range.",
  "details": [
    {
      "field": "pickupDate",
      "message": "pickupDate must be before returnDate"
    }
  ],
  "correlationId": "req-20260509-0001"
}
```

## Error Codes

| Code | HTTP Status | Meaning |
|---|---|---|
| AUTH_INVALID_CREDENTIALS | 401 | Email or password invalid |
| AUTH_TOKEN_EXPIRED | 401 | Access token expired |
| AUTH_ACCOUNT_SUSPENDED | 403 | Account is suspended (returned only after credential check passes) |
| AUTH_ACCOUNT_LOCKED | 423 | Account locked due to too many failed login attempts; `Retry-After` header included |
| ACCESS_DENIED | 403 | User lacks permission |
| USER_EMAIL_EXISTS | 409 | Email already registered |
| DRIVER_LICENSE_NOT_APPROVED | 403 | Customer is not eligible to book |
| ALREADY_SUBMITTED | 409 | Verification already pending/approved |
| VEHICLE_NOT_FOUND | 404 | Vehicle not found |
| VEHICLE_ARCHIVE_NOT_ALLOWED | 409 | Vehicle cannot be archived |
| LISTING_NOT_FOUND | 404 | Listing not found or not visible |
| LISTING_NOT_AVAILABLE | 409 | Listing unavailable for selected dates |
| BOOKING_OVERLAP_CUSTOMER | 409 | Customer has overlapping booking |
| BOOKING_INVALID_STATUS | 409 | Action not allowed for current booking status |
| IDEMPOTENCY_KEY_REQUIRED | 400 | Idempotency-Key required |
| IDEMPOTENCY_KEY_CONFLICT | 409 | Same key used with different body |
| REQUEST_ALREADY_PROCESSING | 409 | Request with same key is still processing |
| PAYMENT_FAILED | 402 | Payment operation failed |
| PAYMENT_VOID_RETRY_REQUIRED | 202 | Cancellation done but void retry required |
| VALIDATION_ERROR | 400 | Request validation failed |
| TOO_MANY_REQUESTS | 429 | Rate limit exceeded |
| INTERNAL_ERROR | 500 | Unexpected server error |

## HTTP Status Code Usage

| Status | When to use |
|---|---|
| 200 | Successful GET, PATCH |
| 201 | Successful POST (resource created) |
| 202 | Accepted (async operation started, e.g., void retry) |
| 400 | Validation errors, invalid idempotency key format |
| 401 | Missing or invalid JWT |
| 402 | Payment operation failed |
| 403 | Access denied (role or resource ownership) |
| 404 | Resource not found |
| 409 | Conflict (duplicate, invalid state transition, business rule violation) |
| 429 | Rate limit exceeded |
| 500 | Unexpected server error (never expose details to client) |

## Security Rules

- Never expose internal error details to client.
- Return correlationId for log tracing.
- Audit log all 401/403 responses with IP address.
- Rate limit 401 responses to prevent user enumeration.
