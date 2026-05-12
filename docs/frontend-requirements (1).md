# RentFlow Frontend Requirements

Version: 2026-05-11  
Source: current backend code plus `docs/srs.md`, phase docs, and API contracts.  
Important: this file defines frontend requirements only. It does not authorize generating frontend code in this backend repo.

## Product Scope

RentFlow is a car rental booking frontend that consumes a Spring Boot REST API. The first frontend build should support the backend features already implemented:

- Guest listing search and listing detail.
- Register, login, refresh token, logout.
- Current user profile view/update.
- Host vehicle management.
- Host listing management and submission.
- Host availability calendar with block/unblock.
- Admin listing approval workflow.
- Admin user list.
- Customer booking hold creation.
- My bookings, booking detail, location patch, and HELD booking cancel.

Payment, driver verification endpoints, notifications, files/photos, trip lifecycle, reviews, disputes, reports, and payouts are planned but not currently implemented in backend.

## Personas

### Guest

Can search active listings, view listing details, inspect public availability, register, and login.

### Customer

Can manage own profile, create booking holds, view own bookings, patch booking pickup/return locations, and cancel HELD bookings.

### Host

Can manage own vehicles, listings, listing lifecycle, and listing availability.

### Admin

Can list users and approve/reject/suspend/reactivate listings.

A user may have multiple roles.

## Global Frontend Requirements

- Use REST JSON; no backend-rendered pages are expected.
- Use role-aware routing and navigation.
- Persist auth state: access token, refresh token, token expiry metadata, current user, and roles.
- Attach `Authorization: Bearer <accessToken>` to protected requests.
- Attempt refresh on `401 AUTH_TOKEN_EXPIRED`; logout if refresh fails.
- Generate UUID-v4 `Idempotency-Key` for `POST /bookings` and `POST /bookings/{id}/cancel`.
- Reuse an idempotency key only when retrying the exact same request body.
- Show loading, empty, optimistic disabled-submit, validation, and error states for every API interaction.
- Render backend `details[]` field errors when `VALIDATION_ERROR` is returned.
- Keep `correlationId` visible in technical details or support copy.
- Dates must be handled as local calendar dates, not instants.
- Booking range validation must enforce `pickupDate < returnDate`, min 1 day, max 30 days, and no past pickup date.
- Frontend must not expose host/admin links to users without the relevant role.

## Recommended Route Map

```text
/
/login
/register
/listings
/listings/:id
/listings/:id/book
/me/profile
/me/bookings
/bookings/:id

/host
/host/vehicles
/host/vehicles/new
/host/vehicles/:id
/host/listings
/host/listings/new
/host/listings/:id
/host/listings/:id/availability

/admin
/admin/listings
/admin/listings/:id
/admin/users
```

Planned routes for later backend phases:

```text
/bookings/:id/payment
/me/driver-verification
/me/notifications
/host/bookings
/admin/driver-verifications
/admin/audit-logs
/admin/reports
```

## App Shell

The frontend should include:

- Header with primary navigation.
- Auth menu with login/register or current user/logout.
- Role-switch/role-aware menu when a user has multiple roles.
- Toast or alert system.
- API error component.
- Confirmation modal component.
- Pagination component that works with both custom `PageResponse<T>` and Spring `Page<T>`.
- Empty states for search, lists, and unavailable resources.

## Auth Requirements

### Register

Fields:

| Field | Rule |
|---|---|
| email | required, valid email |
| password | required, min 8 chars |
| fullName | required |
| roles | optional; default expected backend behavior is customer when omitted |

On success, store returned token response if present and redirect to intended page or `/listings`.

### Login

Fields: email and password.  
On success, store tokens and current user.  
On `AUTH_INVALID_CREDENTIALS`, show a non-field login error.

### Logout

Call backend logout with refresh token, clear local auth state, and redirect to `/login` or public listings.

## Profile Requirements

Route: `/me/profile`

Show and edit:

- full name
- phone
- date of birth
- address line
- email and roles as read-only
- driver verification status as read-only until backend driver verification endpoints exist

## Public Listing Requirements

### Listing Search

Route: `/listings`

Filters:

| Filter | Component |
|---|---|
| city | text input |
| categories | multi-select |
| pickupDate | date input |
| returnDate | date input |
| minPrice/maxPrice | number inputs |
| seats | number input |
| transmission | select |
| fuelType | select |
| page/size | pagination |

Listing card displays:

- title
- city
- category
- base price per day and currency
- seats
- transmission
- fuel type
- cover photo placeholder because current backend returns `coverPhotoUrl: null`
- rating hidden or placeholder because current backend returns `ratingAverage: null`

### Listing Detail

Route: `/listings/:id`

Display:

- title, description, city, address
- price and currency
- daily km limit
- instant book flag
- cancellation policy
- vehicle summary
- extras
- public availability
- booking CTA

Guest booking CTA should redirect to login with intended URL preserved. Customer booking CTA opens booking page.

## Booking Requirements

### Create Booking Hold

Route: `/listings/:id/book`

Fields:

| Field | Required |
|---|---|
| pickupDate | yes |
| returnDate | yes |
| pickupLocation | optional |
| returnLocation | optional |
| extras | optional |

Before submit:

- Validate date range.
- Check customer is authenticated.
- Generate UUID-v4 `Idempotency-Key`.
- Disable submit while the request is in flight.

Success:

- Backend returns `status: HELD`.
- Show `holdExpiresAt` countdown.
- Show booking summary and current limitation: payment confirmation is not available until payment backend is implemented.

### My Bookings

Route: `/me/bookings`

Filters:

```text
ALL, HELD, PENDING_HOST_APPROVAL, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, REJECTED, EXPIRED
```

For current backend, user should mostly see `HELD`, `CANCELLED`, or `EXPIRED`.

### Booking Detail

Route: `/bookings/:id`

Display:

- status
- listing title
- pickup/return dates
- pickup/return locations
- total amount and currency
- hold expiration
- price snapshot
- policy snapshot

Actions:

- Patch pickup/return locations for statuses `HELD`, `PENDING_HOST_APPROVAL`, `CONFIRMED`.
- Cancel only when backend allows it. Current backend allows only `HELD`.

### Cancel Booking Modal

Field: reason, optional, max 500 characters.  
Frontend should strip or block HTML-like input before submit.  
Submit requires UUID-v4 `Idempotency-Key`.

## Host Requirements

### Vehicles

Route group: `/host/vehicles`

Capabilities:

- list by status
- create
- view detail
- update
- archive

Create fields:

- category
- make
- model
- year
- plate number
- VIN optional
- transmission
- fuel type
- seats
- status optional, backend default is `ACTIVE`

Archive is soft delete. If backend returns `VEHICLE_ARCHIVE_NOT_ALLOWED`, explain that related active bookings/listings block archive.

### Listings

Route group: `/host/listings`

Capabilities:

- list by status
- create
- view detail
- update
- submit
- archive
- reactivate

Create fields:

- vehicle
- title
- description
- city
- address
- latitude/longitude optional
- base price per day
- currency, default suggested `VND`
- daily km limit
- instant book
- cancellation policy

Submit requirements:

- listing must be `DRAFT`
- vehicle should be `ACTIVE`
- backend changes status to `PENDING_APPROVAL`

### Availability

Route: `/host/listings/:id/availability`

Display calendar/list statuses:

```text
FREE, HOLD, BOOKED, BLOCKED
```

Actions:

- block selected dates
- unblock selected dates

Frontend should prevent obvious invalid operations:

- cannot block `HOLD` or `BOOKED`
- cannot unblock non-`BLOCKED`

Backend request uses explicit `dates: string[]`, not `{ from, to }`.

## Admin Requirements

### Listing Approval

Route group: `/admin/listings`

Capabilities:

- list by status, hostId, city, page, size
- view detail
- approve
- reject with reason
- suspend with reason
- reactivate

Approval side effect: backend sets listing to `ACTIVE` and generates availability rows.

### Users

Route: `/admin/users`

Filters:

- status
- role
- page
- size

Display:

- email
- roles
- full name
- account status
- driver verification status
- createdAt
- lastLoginAt

## Error Handling Requirements

| Backend code | UI behavior |
|---|---|
| `AUTH_INVALID_CREDENTIALS` | Show login error |
| `AUTH_TOKEN_EXPIRED` | Refresh token, retry once, then logout |
| `ACCESS_DENIED` | Show forbidden or hide unavailable actions |
| `USER_EMAIL_EXISTS` | Show account exists and link to login |
| `LISTING_NOT_FOUND` | Show not found/unavailable listing |
| `LISTING_NOT_AVAILABLE` | Refresh availability and ask user to select other dates |
| `BOOKING_OVERLAP_CUSTOMER` | Show link to my bookings |
| `BOOKING_INVALID_STATUS` | Refresh booking and show current state |
| `IDEMPOTENCY_KEY_REQUIRED` | Treat as frontend bug |
| `IDEMPOTENCY_KEY_CONFLICT` | Do not retry with same key |
| `REQUEST_ALREADY_PROCESSING` | Keep action disabled and show processing state |
| `VEHICLE_ARCHIVE_NOT_ALLOWED` | Explain archive preconditions |
| `VALIDATION_ERROR` | Render field-level messages |
| `TOO_MANY_REQUESTS` | Back off and show retry timing if available |
| `INTERNAL_ERROR` | Generic retry/support message |

## API Client Requirements

The API layer should provide:

- typed DTOs matching `docs/api-contracts.md`
- auth token attachment
- refresh-token retry handling
- normalized error object
- correlation ID support
- idempotency key helper
- date serialization as `YYYY-MM-DD`
- pagination adapter for `PageResponse<T>` and Spring `Page<T>`

## Non-Goals For First Frontend Build

- Do not implement payment UI until backend payment endpoints exist.
- Do not implement driver license upload/verification until endpoints exist.
- Do not implement file/photo upload until file APIs exist.
- Do not implement realtime chat, GPS tracking, mobile app, or analytics-heavy dashboards.
- Do not generate backend code or alter backend behavior from frontend work.

## Acceptance Criteria

- Guest can search and view listing details.
- User can register, login, refresh implicitly, logout.
- Authenticated user can view/update profile.
- Host can create vehicle and listing, submit listing, manage availability.
- Admin can approve/reject/suspend/reactivate listing and list users.
- Customer can create HELD booking and see hold countdown.
- Customer can list, view, patch location, and cancel eligible HELD booking.
- API errors show clear user messages and field errors.
- Navigation and protected routes respect roles.
