# RentFlow Frontend Route Map (Phase 5)

## Public

- `/`
- `/login`
- `/register`
- `/listings`
- `/listings/[id]`

## Customer

- `/listings/[id]/book`
- `/me/profile`
- `/me/bookings`
- `/bookings/[id]`

## Host

- `/host` -> redirect to `/host/dashboard`
- `/host/dashboard`
- `/host/vehicles`
- `/host/vehicles/new`
- `/host/vehicles/[id]`
- `/host/listings`
- `/host/listings/new`
- `/host/listings/[id]`
- `/host/listings/[id]/availability`

## Admin

- `/admin`
- `/admin/listings`
- `/admin/listings/[id]`
- `/admin/users`

## Global States

- `not-found.tsx`
- route-level `error.tsx`
- `/forbidden`
- reusable loading/skeleton/empty/error states

## Deferred Routes (Inactive)

- `/bookings/[id]/payment`
- `/me/driver-verification`
- `/me/notifications`
- `/host/bookings`
- `/admin/driver-verifications`
- `/admin/audit-logs`
- `/admin/reports`
