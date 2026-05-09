# Frontend Requirements — RentFlow

**Version:** 1.1  
**Synced with:** RentFlow SRS v5.1  
**Frontend target:** React / Next.js / Vue-compatible  
**Backend style:** REST API-first, JSON response, JWT authentication

---

## 1. Purpose

The frontend must consume the RentFlow REST API and must not rely on backend-rendered pages.

The UI should support the P0 backend demo first:

```text
Guest searches listings.
Customer creates HELD booking.
Booking hold countdown is shown.
Host can create/submit listing.
Admin can approve listing.
```

P1/P2 screens are added after P0 backend is stable.

---

## 2. Scope by Phase

### P0 — Required Frontend Scope

| Area | Required UI |
|---|---|
| Auth | Register, login, logout |
| User | View/update own profile |
| Public listing | Search listings, view listing detail |
| Host | Create vehicle, create listing, submit listing |
| Admin | Approve/reject listing |
| Availability | Public listing availability; host full availability |
| Booking | Create booking hold |
| Booking expiry | Show hold expiration timer |
| Error handling | Standard API error response support |

### P1 — Business Complete UI

| Area | Required UI |
|---|---|
| Payment | Authorize payment |
| Cancellation | Cancel booking with reason |
| Driver verification | Submit license metadata/document |
| Notification | Notification list |
| Timeline | Booking timeline if backend exposes it |
| Host approval | Host approve/reject booking if `instantBook=false` |
| File metadata | Listing photo metadata and document metadata when API is available |

### P2 — Advanced UI

| Area | Required UI |
|---|---|
| MinIO full flow | Signed upload/download URLs |
| Trip lifecycle | Check-in/check-out |
| Reviews | Review completed trip |
| Disputes | Open/resolve dispute |
| Reports | Admin/host dashboards |
| Payouts | Host earnings/payout view |

---

## 3. Personas and Permissions

### Guest

Can search active listings, view listing details, register, and login. Cannot create booking or view host internal availability.

### Customer

Can manage profile, search listings, create bookings, authorize payment, cancel eligible bookings, view own bookings, submit driver verification, and view notifications.

Customer cannot access another customer's booking, book own hosted listing, or create new booking when driver verification is required and status is not `APPROVED`.

### Host

Can manage own vehicles/listings, submit listings for approval, view own listing full availability, block/unblock dates, and handle host bookings in P1.

Host cannot view customer license number/document, customer payment details, customer phone/address in pending approval, or manage another host's resources.

### Admin

Can approve/reject listings, view users, approve/reject driver verification, view audit logs, and view reports in P2.

---

## 4. Global Frontend Requirements

### App Shell

The app should include:

- Header/navigation
- Auth state indicator
- Role-aware navigation
- Responsive layout
- Toast/notification system
- Loading states
- Empty states
- Error boundary
- API error display component

### Auth State

Frontend stores:

- Access token
- Refresh token
- Current user summary
- Roles
- Token expiry metadata if provided

Rules:

```text
Attach Authorization: Bearer <accessToken> to protected API calls.
Refresh token on 401 AUTH_TOKEN_EXPIRED.
Logout on refresh failure.
Hide role-restricted navigation when user lacks role.
```

### Error Handling

Frontend must understand:

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

Display `message`, render `details[]` as field errors, and keep `correlationId` for debug/support.

### Date Handling

Rules:

```text
pickupDate < returnDate
minimum rental = 1 day
maximum rental = 30 days
booking period = [pickupDate, returnDate)
returnDate is exclusive
```

Example:

```text
pickupDate = 2026-06-01
returnDate = 2026-06-03
Occupied dates: 2026-06-01 and 2026-06-02
```

### Idempotency Handling

Generate UUID-v4 `Idempotency-Key` before:

- Create booking
- Authorize payment
- Cancel booking
- Capture payment
- Void payment
- Refund payment
- Host approve booking
- Host reject booking

Reuse the same key only for retrying the same exact request body.

---

## 5. Page Requirements

### Listing Search Page

Route: `/listings`

Filters:

| Filter | Type |
|---|---|
| city | text |
| category | multi-select |
| pickupDate | date |
| returnDate | date |
| minPrice | number |
| maxPrice | number |
| seats | number |
| transmission | select |
| fuelType | select |

Listing card shows title, city, category, price, currency, seats, transmission, fuel type, cover photo if available, and rating average if available. `ratingAverage` is P2 and may be null/omitted.

### Listing Detail Page

Route: `/listings/:id`

Shows listing info, vehicle summary, description, city/address summary, price, cancellation policy, availability calendar, extras if available, and Book CTA.

Guest clicking Book redirects to login/register. Customer proceeds to booking form.

### Create Booking Page

Route: `/listings/:id/book`

Fields:

| Field | Required |
|---|---|
| pickupDate | Yes |
| returnDate | Yes |
| pickupLocation | Optional/P0 |
| returnLocation | Optional/P0 |
| extras | Optional |

Before submit: validate date range, max 30 days, user logged in, and driver verification gate if enabled.

On success, booking status is `HELD`; show countdown using `holdExpiresAt`.

### My Bookings Page

Route: `/me/bookings`

Filters: All, HELD, PENDING_HOST_APPROVAL, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, REJECTED, EXPIRED.

Status actions:

| Status | Customer action |
|---|---|
| HELD | Continue payment, cancel |
| PENDING_HOST_APPROVAL | View status, cancel |
| CONFIRMED | View detail, cancel |
| IN_PROGRESS | View detail |
| COMPLETED | Review, P2 |
| CANCELLED/REJECTED/EXPIRED | View detail |

### Cancel Booking Modal

Allowed for HELD, PENDING_HOST_APPROVAL, CONFIRMED. Not allowed for IN_PROGRESS or COMPLETED.

Reason must be max 500 chars and stripped/rejected for HTML/script content.

### Host Vehicles Page

Route: `/host/vehicles`

Capabilities: list, create, edit, archive. Vehicle statuses: DRAFT, ACTIVE, MAINTENANCE, SUSPENDED, ARCHIVED.

Default: new vehicles are ACTIVE unless host explicitly saves DRAFT.

Archive is soft delete; backend rejects if active bookings exist.

### Host Listings Page

Route: `/host/listings`

Capabilities: create, edit, submit, archive, reactivate.

Submit preconditions:

```text
listing.status = DRAFT
vehicle.status = ACTIVE
photos recommended but not required for P0/P1
```

### Host Availability Page

Route: `/host/listings/:id/availability`

Host sees FREE, HOLD, BOOKED, BLOCKED. Host can block FREE dates and unblock BLOCKED dates. Host cannot block HOLD/BOOKED dates.

### Admin Listing Approval Page

Route: `/admin/listings`

Actions: approve, reject with reason, suspend with reason, reactivate.

Approval side effect: listing becomes ACTIVE and backend generates 365 availability rows.

### Driver Verification Page

Customer route: `/me/driver-verification`. Admin route: `/admin/driver-verifications`.

Customer can view current status, submit license number, expiry date, and documentFileId if file flow exists. Admin can approve/reject with reason.

### Notifications Page

Route: `/me/notifications`

Shows type, title, message, created time, read status, and delivery status.

---

## 6. Conflict Handling

| Backend code | UI behavior |
|---|---|
| LISTING_NOT_AVAILABLE | Show unavailable message and refresh calendar |
| BOOKING_OVERLAP_CUSTOMER | Show existing booking warning |
| BOOKING_INVALID_STATUS | Refresh resource and show current status |
| IDEMPOTENCY_KEY_CONFLICT | Do not retry with same key |
| REQUEST_ALREADY_PROCESSING | Show processing state |
| VEHICLE_ARCHIVE_NOT_ALLOWED | Show active booking/listing constraint |
| PAYMENT_VOID_RETRY_REQUIRED | Mark cancellation done and show payment-release warning |

---

## 7. Recommended Frontend Architecture

```text
src/
├── app or pages
├── components
├── features
│   ├── auth
│   ├── listings
│   ├── bookings
│   ├── vehicles
│   ├── host
│   ├── admin
│   ├── notifications
│   └── profile
├── lib
│   ├── api
│   ├── auth
│   ├── errors
│   ├── idempotency
│   └── date
└── types
```

API client responsibilities:

- Attach access token
- Attach correlation ID
- Handle refresh token
- Normalize error response
- Generate idempotency keys for mutation actions
- Provide typed DTOs

---

## 8. Non-Goals

P0/P1 frontend should not implement realtime chat, GPS tracking, complex maps, microfrontends, full payment gateway UI, mobile app, or advanced analytics dashboard.

---

## 9. Acceptance Criteria

### P0

- Guest can search listings.
- Customer can register/login.
- Host can create vehicle/listing and submit listing.
- Admin can approve listing.
- Customer can create HELD booking.
- HELD booking shows countdown.
- API errors display user-friendly messages.
- Role-based navigation works.

### P1

- Customer can authorize payment.
- Customer can cancel booking.
- Driver verification UI exists.
- Notifications UI exists.
- Host full availability calendar exists.
- Admin driver verification screen exists.

### P2

- File upload/photo UI exists.
- Check-in/check-out flow exists.
- Review/dispute/report screens exist.
