# RentFlow UI User Flow Spec

Version: 2026-05-11  
Source: current backend code plus SRS/phase docs.  
Audience: frontend developer and UI designer.

## Navigation By Role

### Guest

Visible:

- Search cars
- Login
- Register

Hidden:

- My bookings
- Host
- Admin
- Profile

### Customer

Visible:

- Search cars
- My bookings
- Profile
- Logout

Conditional:

- Host links if user also has `HOST`
- Admin links if user also has `ADMIN`

### Host

Visible:

- Host vehicles
- Host listings
- Host listing availability
- Profile
- Logout

### Admin

Visible:

- Admin listings
- Admin users
- Profile
- Logout

Driver verification, notifications, audit logs, reports, host booking approval, and payments are future flows until backend endpoints exist.

## Flow 1: Guest Searches Cars

Goal: user finds a rentable car.

Steps:

```text
1. Guest opens /listings.
2. UI displays filters and an initial listing page.
3. Guest enters city, optional dates, and optional vehicle filters.
4. UI validates date pair when both dates exist.
5. UI calls GET /api/v1/listings.
6. UI renders listing cards.
7. Guest opens a listing detail page.
8. UI calls GET /api/v1/listings/{id}.
9. UI calls GET /api/v1/listings/{id}/availability when a date window is needed.
```

Empty state:

```text
No cars match the selected filters.
```

Errors:

| Error | UI response |
|---|---|
| `VALIDATION_ERROR` | Show filter-level error |
| `LISTING_NOT_FOUND` | Show unavailable listing page |
| `INTERNAL_ERROR` | Show retry action |

## Flow 2: Register And Login

### Register

```text
1. Guest opens /register.
2. Guest enters email, password, full name, and optional roles.
3. UI calls POST /api/v1/auth/register.
4. UI stores returned auth tokens when present.
5. UI redirects to intended page or /listings.
```

### Login

```text
1. Guest opens /login.
2. Guest enters email and password.
3. UI calls POST /api/v1/auth/login.
4. UI stores tokens and current user.
5. UI redirects to intended page or /listings.
```

Error handling:

| Error | UI response |
|---|---|
| `AUTH_INVALID_CREDENTIALS` | Show login error |
| `USER_EMAIL_EXISTS` | Link to login |
| `VALIDATION_ERROR` | Show field errors |

## Flow 3: Profile Update

```text
1. Authenticated user opens /me/profile.
2. UI calls GET /api/v1/users/me.
3. User edits full name, phone, date of birth, or address.
4. UI calls PATCH /api/v1/users/me.
5. UI updates local current-user state from the response.
```

Driver verification status is read-only in current backend.

## Flow 4: Customer Creates Booking Hold

Preconditions:

```text
User is authenticated.
User has CUSTOMER role.
Listing exists and is ACTIVE.
Vehicle is ACTIVE.
pickupDate < returnDate.
Rental duration is between 1 and 30 days.
Selected availability rows are FREE.
Customer is not booking own hosted listing.
Customer has no overlapping active booking.
```

Steps:

```text
1. Customer opens /listings/:id.
2. Customer selects pickupDate and returnDate.
3. UI validates date range.
4. Customer opens /listings/:id/book.
5. Customer enters pickup/return locations and optional extras.
6. UI generates UUID-v4 Idempotency-Key.
7. UI calls POST /api/v1/bookings.
8. Backend returns status HELD.
9. UI shows booking detail and a countdown to holdExpiresAt.
```

Success state:

```text
Booking is held. Payment confirmation is not available until payment backend is implemented.
```

Conflict handling:

| Backend code | UI behavior |
|---|---|
| `LISTING_NOT_AVAILABLE` | Refresh availability and ask for another date range |
| `BOOKING_OVERLAP_CUSTOMER` | Link to /me/bookings |
| `DRIVER_LICENSE_NOT_APPROVED` | Future: route to driver verification |
| `REQUEST_ALREADY_PROCESSING` | Keep submit disabled and show processing |
| `IDEMPOTENCY_KEY_CONFLICT` | Stop retrying with the same key |

## Flow 5: Customer Views Bookings

```text
1. Customer opens /me/bookings.
2. UI calls GET /api/v1/bookings/me.
3. Customer filters by status if needed.
4. UI calls GET /api/v1/bookings/me?status={status}.
5. Customer opens /bookings/:id.
6. UI calls GET /api/v1/bookings/{id}.
```

Current actions by status:

| Status | UI action |
|---|---|
| `HELD` | View detail, patch locations, cancel |
| `CANCELLED` | View detail |
| `EXPIRED` | View detail, book again manually |
| Other statuses | Display read-only until later backend phases |

## Flow 6: Customer Patches Booking Locations

```text
1. Customer opens /bookings/:id.
2. Booking status is HELD, PENDING_HOST_APPROVAL, or CONFIRMED.
3. Customer edits pickupLocation and/or returnLocation.
4. UI calls PATCH /api/v1/bookings/{id}.
5. UI replaces local booking detail with response.
```

Rules:

- At least one location field must be non-null.
- Do not send unknown fields.
- Do not allow date/status/listing edits through this endpoint.

## Flow 7: Customer Cancels HELD Booking

Current backend allows only `HELD` cancellation.

```text
1. Customer opens /bookings/:id.
2. Customer clicks Cancel.
3. UI opens confirmation modal.
4. Customer optionally enters reason.
5. UI enforces max 500 chars and strips/blocks HTML-like input.
6. UI generates UUID-v4 Idempotency-Key.
7. UI calls POST /api/v1/bookings/{id}/cancel.
8. Backend returns status CANCELLED.
9. UI refreshes booking detail and availability if user came from listing page.
```

Errors:

| Error | UI response |
|---|---|
| `BOOKING_INVALID_STATUS` | Refresh detail and disable cancel |
| `BOOKING_NOT_FOUND` | Show not found |
| `IDEMPOTENCY_KEY_CONFLICT` | Stop retrying with same key |

## Flow 8: Host Creates Vehicle

```text
1. Host opens /host/vehicles/new.
2. Host enters vehicle category, make, model, year, plate, transmission, fuel, seats, and optional status.
3. UI calls POST /api/v1/host/vehicles.
4. Backend returns created vehicle.
5. UI routes to vehicle detail or prompts host to create listing.
```

Notes:

- If status is omitted, backend defaults to `ACTIVE`.
- Plate number and VIN are submitted but not returned in `VehicleResponse`.

## Flow 9: Host Updates Or Archives Vehicle

```text
1. Host opens /host/vehicles/:id.
2. UI calls GET /api/v1/host/vehicles/{id}.
3. Host edits allowed fields.
4. UI calls PATCH /api/v1/host/vehicles/{id}.
5. Host can archive using DELETE /api/v1/host/vehicles/{id}.
```

Archive rejection:

```text
If active related bookings exist, show that the vehicle cannot be archived yet.
```

## Flow 10: Host Creates And Submits Listing

```text
1. Host opens /host/listings/new.
2. UI loads host vehicles.
3. Host selects an ACTIVE vehicle.
4. Host enters listing content, location, price, policy, and booking options.
5. UI calls POST /api/v1/host/listings.
6. Backend returns listing in DRAFT.
7. Host clicks Submit.
8. UI calls POST /api/v1/host/listings/{id}/submit.
9. Backend returns listing in PENDING_APPROVAL.
```

Submit preconditions:

- listing status is `DRAFT`
- vehicle status is `ACTIVE`

## Flow 11: Host Manages Listings

```text
1. Host opens /host/listings.
2. UI calls GET /api/v1/host/listings.
3. Host filters by status.
4. Host opens /host/listings/:id.
5. UI calls GET /api/v1/host/listings/{id}.
6. Host edits, archives, reactivates, or submits based on current status.
```

Status behavior:

| Status | Common actions |
|---|---|
| `DRAFT` | edit, submit, archive |
| `PENDING_APPROVAL` | view, archive |
| `ACTIVE` | view, archive |
| `SUSPENDED` | view, reactivate if backend permits |
| `ARCHIVED` | view only |

## Flow 12: Host Manages Availability

```text
1. Host opens /host/listings/:id/availability.
2. UI calls GET /api/v1/host/listings/{id}/availability?from=&to=.
3. UI renders calendar or date list with FREE/HOLD/BOOKED/BLOCKED.
4. Host selects dates.
5. Host clicks Block or Unblock.
6. UI calls the matching block/unblock endpoint with { dates: [...] }.
7. UI refreshes availability.
```

Client-side restrictions:

- block only `FREE`
- unblock only `BLOCKED`
- `HOLD` and `BOOKED` are read-only

## Flow 13: Admin Reviews Listings

```text
1. Admin opens /admin/listings.
2. UI calls GET /api/v1/admin/listings?status=PENDING_APPROVAL.
3. Admin opens /admin/listings/:id.
4. UI calls GET /api/v1/admin/listings/{id}.
5. Admin approves, rejects with reason, suspends with reason, or reactivates.
6. UI calls the matching admin listing endpoint.
7. UI refreshes list/detail.
```

Approve result:

```text
Listing becomes ACTIVE and backend generates availability rows.
```

Reject result:

```text
Listing returns to DRAFT.
```

## Flow 14: Admin Lists Users

```text
1. Admin opens /admin/users.
2. UI calls GET /api/v1/admin/users.
3. Admin filters by status or role.
4. UI renders paginated user summaries.
```

This is read-only in current backend.

## Flow 15: Token Expiry Recovery

```text
1. API call returns 401 AUTH_TOKEN_EXPIRED.
2. UI calls POST /api/v1/auth/refresh with refreshToken.
3. If refresh succeeds, UI stores new tokens and retries original request once.
4. If refresh fails, UI clears auth state and redirects to /login.
```

## Flow 16: Idempotency Recovery

```text
1. User submits create booking or cancel booking.
2. UI generates one UUID-v4 Idempotency-Key for that user action.
3. Network fails or user retries the same action.
4. UI retries with the same key and same body.
5. If user changes form/body, UI starts a new action with a new key.
```

Error behavior:

- `REQUEST_ALREADY_PROCESSING`: show processing and avoid rapid retries.
- `IDEMPOTENCY_KEY_CONFLICT`: do not retry with same key.

## Future Flows Waiting For Backend

These flows are documented in SRS but should stay disabled or hidden until endpoints are implemented:

- customer payment authorization
- payment capture/void/refund
- host booking approval/rejection
- customer driver license submission
- admin driver verification
- listing photo/file upload
- notifications
- audit logs
- revenue and host earnings reports
- trip check-in/check-out
- reviews and disputes

## P0 UI Acceptance Criteria

- Guest can search listings and open listing details.
- User can register, login, logout, and recover access token through refresh.
- User can view and update profile.
- Host can create/update/archive vehicles.
- Host can create/update/submit/archive/reactivate listings.
- Host can block/unblock listing availability dates.
- Admin can approve/reject/suspend/reactivate listings.
- Admin can list users.
- Customer can create a HELD booking with idempotency.
- Customer can see hold countdown.
- Customer can view bookings and booking detail.
- Customer can patch booking locations.
- Customer can cancel HELD booking with idempotency.
- API validation, authorization, not-found, conflict, and processing states render clearly.
