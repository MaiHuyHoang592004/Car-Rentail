# UI User Flow Spec — RentFlow

**Version:** 1.1  
**Synced with:** RentFlow SRS v5.1  
**Audience:** Frontend developer, product reviewer, UI designer

---

## 1. Purpose

This document describes the main user flows for the RentFlow UI. The frontend consumes REST APIs and must not rely on backend-rendered pages.

---

## 2. Role-Based Navigation

### Guest

Visible: Home, Search Cars, Login, Register.  
Hidden: My Bookings, Host Dashboard, Admin Dashboard, Notifications.

### Customer

Visible: Search Cars, My Bookings, Profile, Driver Verification, Notifications, Logout.  
Conditional: Host Dashboard if user has HOST role, Admin Dashboard if user has ADMIN role.

### Host

Visible: Host Dashboard, Vehicles, Listings, Availability, Host Bookings, Profile, Notifications, Logout.

### Admin

Visible: Admin Dashboard, Listing Approvals, Driver Verifications, Users, Audit Logs, Reports P2, Logout.

---

## 3. Flow 1 — Guest Searches Listings

Goal: guest finds available cars.

Steps:

```text
1. Guest opens /listings.
2. UI shows search filters.
3. Guest enters city and optional dates.
4. UI validates date pair if both dates are entered.
5. UI calls GET /api/v1/listings.
6. UI renders listing cards.
7. Guest clicks a listing.
8. UI opens listing detail page.
```

Empty state:

```text
No cars found for your search. Try changing dates, city, or filters.
```

Errors:

| Error | UI response |
|---|---|
| VALIDATION_ERROR | Show field error |
| INTERNAL_ERROR | Show retry CTA |
| TOO_MANY_REQUESTS | Show Retry-After message |

---

## 4. Flow 2 — Register and Login

Register:

```text
1. Guest opens /register.
2. Guest enters email, password, full name, and optional role choice.
3. UI calls POST /api/v1/auth/register.
4. On success, redirect to login or auto-login if backend supports it.
```

Login:

```text
1. Guest opens /login.
2. Guest enters email/password.
3. UI calls POST /api/v1/auth/login.
4. UI stores auth state.
5. UI redirects to intended page or /listings.
```

Error handling:

| Error | UI response |
|---|---|
| AUTH_INVALID_CREDENTIALS | Show email/password error |
| USER_EMAIL_EXISTS | Link to login |
| TOO_MANY_REQUESTS | Disable form temporarily |

---

## 5. Flow 3 — Customer Creates Booking

Preconditions:

```text
User is authenticated as CUSTOMER.
Listing is ACTIVE.
Vehicle is ACTIVE.
Selected dates are available.
pickupDate < returnDate.
Rental duration <= 30 days.
Driver verification is APPROVED if gate is enabled.
```

Steps:

```text
1. Customer opens listing detail.
2. Customer selects pickupDate and returnDate.
3. UI validates pickupDate < returnDate.
4. UI validates rental duration <= 30 days.
5. Customer clicks Book.
6. UI generates Idempotency-Key.
7. UI calls POST /api/v1/bookings.
8. Backend returns booking status HELD.
9. UI shows hold timer and Continue Payment CTA if payment phase is available.
```

Success:

```text
Booking is held for 15 minutes. Please authorize payment to confirm your booking.
```

Conflicts:

| Backend code | UI behavior |
|---|---|
| LISTING_NOT_AVAILABLE | Show unavailable message and refresh availability |
| BOOKING_OVERLAP_CUSTOMER | Show link to user's existing booking |
| DRIVER_LICENSE_NOT_APPROVED | Show driver verification CTA |
| REQUEST_ALREADY_PROCESSING | Show processing state and retry status |
| IDEMPOTENCY_KEY_CONFLICT | Start a new booking action with a new key |

---

## 6. Flow 4 — Customer Authorizes Payment

P1.

Steps:

```text
1. Customer opens /bookings/:id/payment.
2. UI shows booking summary and amount.
3. Customer clicks Authorize Payment.
4. UI generates Idempotency-Key.
5. UI calls POST /api/v1/bookings/{id}/payments/authorize.
6. Backend returns booking and payment status.
```

Instant booking success:

```text
booking.status = CONFIRMED
payment.status = AUTHORIZED
```

Manual approval success:

```text
booking.status = PENDING_HOST_APPROVAL
payment.status = AUTHORIZED
```

Errors:

| Backend code | UI behavior |
|---|---|
| BOOKING_INVALID_STATUS | Refresh booking detail |
| PAYMENT_FAILED | Show payment retry CTA |
| REQUEST_ALREADY_PROCESSING | Keep button disabled temporarily |

---

## 7. Flow 5 — Customer Views My Bookings

Route: `/me/bookings`

Steps:

```text
1. Customer opens My Bookings.
2. UI calls GET /api/v1/bookings/me.
3. UI renders booking cards grouped/filterable by status.
```

Status actions:

| Status | Primary action |
|---|---|
| HELD | Continue payment / cancel |
| PENDING_HOST_APPROVAL | View status / cancel |
| CONFIRMED | View detail / cancel |
| IN_PROGRESS | View trip |
| COMPLETED | Review, P2 |
| CANCELLED | View detail |
| REJECTED | View detail |
| EXPIRED | Book again |

---

## 8. Flow 6 — Customer Cancels Booking

Allowed: HELD, PENDING_HOST_APPROVAL, CONFIRMED.  
Not allowed: IN_PROGRESS, COMPLETED.

Steps:

```text
1. Customer opens booking detail.
2. Customer clicks Cancel.
3. UI opens confirmation modal.
4. UI shows cancellation policy.
5. Customer enters reason.
6. UI validates reason max 500 chars and strips/rejects HTML.
7. UI generates Idempotency-Key.
8. UI calls POST /api/v1/bookings/{id}/cancel.
9. UI shows result.
```

Success states:

```text
Booking cancelled.
```

```text
Booking cancelled. The remaining payment authorization will be released shortly.
```

---

## 9. Flow 7 — Host Creates Vehicle

Route: `/host/vehicles/new`

Steps:

```text
1. Host opens Create Vehicle.
2. Host enters vehicle data.
3. If status omitted, backend creates ACTIVE vehicle.
4. Host may explicitly save as DRAFT.
5. UI calls POST /api/v1/host/vehicles.
6. UI redirects to vehicle detail or listing creation.
```

Notes:

```text
DRAFT vehicle cannot be linked to ACTIVE listing.
ACTIVE vehicle can be used for listing submission.
```

---

## 10. Flow 8 — Host Creates and Submits Listing

Route: `/host/listings/new`

Steps:

```text
1. Host selects own ACTIVE vehicle.
2. Host enters title, description, city, address, price, and policy.
3. UI calls POST /api/v1/host/listings.
4. Backend creates listing in DRAFT.
5. Host clicks Submit.
6. UI calls POST /api/v1/host/listings/{id}/submit.
7. Backend changes status DRAFT -> PENDING_APPROVAL.
```

Submit preconditions:

```text
listing.status = DRAFT
vehicle.status = ACTIVE
photos recommended but not required for P0/P1
```

---

## 11. Flow 9 — Admin Approves Listing

Route: `/admin/listings`

Steps:

```text
1. Admin opens pending listings.
2. UI calls GET /api/v1/admin/listings?status=PENDING_APPROVAL.
3. Admin opens listing detail.
4. Admin clicks Approve.
5. UI calls POST /api/v1/admin/listings/{id}/approve.
6. Backend sets listing ACTIVE and generates 365 availability rows.
7. UI shows approved status.
```

Reject:

```text
1. Admin clicks Reject.
2. UI asks for reason.
3. UI calls POST /api/v1/admin/listings/{id}/reject.
4. Backend returns listing to DRAFT.
```

---

## 12. Flow 10 — Host Manages Availability

Route: `/host/listings/:id/availability`

Steps:

```text
1. Host opens listing availability.
2. UI calls GET /api/v1/host/listings/{id}/availability.
3. UI renders calendar with FREE/HOLD/BOOKED/BLOCKED.
4. Host selects FREE date range.
5. Host clicks Block.
6. UI calls POST /api/v1/host/listings/{id}/availability/block.
```

Restrictions:

```text
Cannot block HOLD dates.
Cannot block BOOKED dates.
Can unblock only BLOCKED dates.
```

---

## 13. Flow 11 — Host Approves Booking

P1 only. Route: `/host/bookings`

Steps:

```text
1. Host opens booking requests.
2. UI calls GET /api/v1/host/bookings?status=PENDING_HOST_APPROVAL.
3. Host opens booking detail.
4. UI shows allowed customer info only.
5. Host clicks Approve or Reject.
6. UI generates Idempotency-Key.
7. UI calls approve/reject endpoint.
```

Host can see customer full name, driver verification status, booking dates, listing/vehicle summary, price snapshot. Host cannot see license number, license document, customer address, phone, or payment details.

---

## 14. Flow 12 — Driver Verification

Customer route: `/me/driver-verification`

Steps:

```text
1. Customer opens verification page.
2. UI shows current status.
3. Customer enters license number and expiry date.
4. Customer attaches document if file flow is available.
5. UI calls POST /api/v1/users/me/driver-license.
6. Backend returns PENDING.
```

Duplicate active submission shows `ALREADY_SUBMITTED`.

Admin route: `/admin/driver-verifications`

Steps:

```text
1. Admin filters PENDING requests.
2. Admin opens detail.
3. Admin approves or rejects with reason.
4. Backend updates customer driver verification status.
```

Expiry behavior:

```text
Daily backend job may change status to EXPIRED.
Customer cannot create new bookings until re-verified.
Existing bookings remain valid.
```

---

## 15. Flow 13 — Vehicle Archive

Route: `/host/vehicles/:id`

Steps:

```text
1. Host opens vehicle detail.
2. Host clicks Archive.
3. UI shows warning that archive is soft delete.
4. UI calls DELETE /api/v1/host/vehicles/{id}.
5. Backend checks related listings/bookings.
6. If allowed, backend archives all non-ARCHIVED listings first.
7. Backend archives vehicle.
```

Reject conditions:

```text
Any HELD booking exists.
Any CONFIRMED booking exists.
Any IN_PROGRESS booking exists.
Archive preconditions fail.
```

---

## 16. Flow 14 — Notifications

Route: `/me/notifications`

Steps:

```text
1. User opens notifications page.
2. UI calls GET /api/v1/notifications/me.
3. UI renders notifications by createdAt.
```

Common notification types:

```text
BOOKING_CONFIRMED
BOOKING_CANCELLED
BOOKING_EXPIRED
PAYMENT_AUTHORIZED
PAYMENT_VOID_RETRY_REQUIRED
DRIVER_VERIFICATION_APPROVED
DRIVER_VERIFICATION_REJECTED
DRIVER_VERIFICATION_EXPIRED
```

---

## 17. Flow 15 — Error Recovery

### Token Expired

```text
1. API returns 401 AUTH_TOKEN_EXPIRED.
2. UI calls refresh token endpoint.
3. If refresh succeeds, retry original request.
4. If refresh fails, logout and redirect to login.
```

### Request Already Processing

```text
1. API returns 409 REQUEST_ALREADY_PROCESSING.
2. UI disables submit button.
3. UI shows "Your request is being processed."
4. UI may retry status fetch after delay.
```

### Idempotency Conflict

```text
1. API returns 409 IDEMPOTENCY_KEY_CONFLICT.
2. UI must not retry with same key.
3. UI should generate a new key only if user intentionally starts a new action.
```

### Listing Not Available

```text
1. API returns 409 LISTING_NOT_AVAILABLE.
2. UI refreshes availability calendar.
3. UI suggests choosing other dates.
```

---

## 18. Recommended Routes

```text
/
/login
/register
/listings
/listings/:id
/listings/:id/book
/bookings/:id
/bookings/:id/payment
/me/profile
/me/bookings
/me/driver-verification
/me/notifications

/host
/host/vehicles
/host/vehicles/new
/host/vehicles/:id
/host/listings
/host/listings/new
/host/listings/:id
/host/listings/:id/availability
/host/bookings

/admin
/admin/listings
/admin/listings/:id
/admin/users
/admin/driver-verifications
/admin/audit-logs
```

---

## 19. UI Acceptance Criteria

### P0

```text
Guest can search listings.
Customer can register/login.
Host can create vehicle.
Host can create and submit listing.
Admin can approve listing.
Customer can create HELD booking.
HELD booking shows countdown.
API errors render correctly.
Role-based navigation works.
```

### P1

```text
Customer can authorize payment.
Customer can cancel booking.
Host can view full availability.
Driver verification flow works.
Notifications page works.
Vehicle archive rules show useful errors.
```

### P2

```text
Photos/files flow works.
Trip check-in/out works.
Reviews/disputes/reports work.
```
