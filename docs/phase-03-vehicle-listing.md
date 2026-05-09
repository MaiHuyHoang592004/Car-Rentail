# Phase 03 — Vehicle + Listing Lifecycle

## Goal

Implement vehicle management and listing lifecycle with state machines, admin approval, availability generation, and vehicle/listing coupling rules.

## Must Implement

- [ ] `Vehicle` entity + repository
- [ ] `VehicleService` with full CRUD
- [ ] Vehicle state machine transitions (DRAFT, ACTIVE, MAINTENANCE, SUSPENDED, ARCHIVED)
- [ ] Vehicle soft delete: sets status to ARCHIVED
- [ ] Vehicle archive preconditions: no HELD / CONFIRMED / IN_PROGRESS bookings exist for any related listing
- [ ] Vehicle archive behavior (in same transaction):
  1. Lock vehicle FOR UPDATE
  2. Query all listings for this vehicle
  3. Query all bookings for all related listings
  4. If any booking status is HELD, CONFIRMED, or IN_PROGRESS -> reject with 409
  5. Otherwise: archive all non-ARCHIVED listings first
  6. Then archive vehicle
- [ ] Vehicle/listing coupling: vehicle MAINTENANCE/SUSPENDED -> ACTIVE listings become SUSPENDED
- [ ] Vehicle default status ACTIVE (unless host explicitly saves as DRAFT)
- [ ] `VehicleController` at `/api/v1/host/vehicles`
- [ ] `Listing` entity + repository
- [ ] `ListingService` with full CRUD
- [ ] Listing state machine (DRAFT, PENDING_APPROVAL, ACTIVE, SUSPENDED, ARCHIVED)
- [ ] Listing submit: DRAFT -> PENDING_APPROVAL (atomic UPDATE WHERE status=DRAFT)
- [ ] Listing cannot be archived with HELD/CONFIRMED/IN_PROGRESS bookings
- [ ] One ACTIVE listing per vehicle constraint
- [ ] Vehicle must be ACTIVE before listing can become ACTIVE
- [ ] Admin listing service: approve, reject, suspend, reactivate
- [ ] Approval to ACTIVE: generate 365 availability rows synchronously in same transaction
- [ ] `ListingController` at `/api/v1/host/listings`
- [ ] `AdminListingController` at `/api/v1/admin/listings`
- [ ] `AvailabilityCalendar` entity + repository
- [ ] `AvailabilityService.generateForListing(listingId)`: creates 365 rows
- [ ] Price calculation: base amount, extras (PER_DAY, PER_TRIP)

## Must Not Implement

- [ ] Payment
- [ ] Driver verification
- [ ] Booking creation
- [ ] Search with availability filtering
- [ ] File uploads / listing photos
- [ ] Audit logging (yet)
- [ ] Timeline entries (yet)

## Files/Modules Expected

```
com.rentflow.vehicle/
├── controller/
│   └── VehicleController.java
├── service/
│   ├── VehicleService.java
│   └── VehicleStateMachine.java
├── entity/
│   └── Vehicle.java
├── repository/
│   └── VehicleRepository.java
└── dto/
    ├── CreateVehicleRequest.java
    ├── UpdateVehicleRequest.java
    └── VehicleResponse.java

com.rentflow.listing/
├── controller/
│   ├── ListingController.java
│   └── AdminListingController.java
├── service/
│   ├── ListingService.java
│   └── ListingStateMachine.java
├── entity/
│   ├── Listing.java
│   └── Extra.java
├── repository/
│   ├── ListingRepository.java
│   └── ExtraRepository.java
└── dto/
    ├── CreateListingRequest.java
    ├── UpdateListingRequest.java
    ├── ListingResponse.java
    └── CreateExtraRequest.java

com.rentflow.common.exception/
└── (add new exceptions as needed)
```

## API Contracts

### Host Vehicle Endpoints

```
POST   /api/v1/host/vehicles
GET    /api/v1/host/vehicles
PATCH  /api/v1/host/vehicles/{id}
DELETE /api/v1/host/vehicles/{id}
```

### Host Listing Endpoints

```
POST   /api/v1/host/listings
GET    /api/v1/host/listings?status=&page=&size=
PATCH  /api/v1/host/listings/{id}
POST   /api/v1/host/listings/{id}/submit
POST   /api/v1/host/listings/{id}/archive
POST   /api/v1/host/listings/{id}/reactivate
```

### Admin Listing Endpoints

```
GET  /api/v1/admin/listings?status=&hostId=&city=&page=&size=
GET  /api/v1/admin/listings/{id}
POST /api/v1/admin/listings/{id}/approve
POST /api/v1/admin/listings/{id}/reject
POST /api/v1/admin/listings/{id}/suspend
POST /api/v1/admin/listings/{id}/reactivate
```

## State Machine Rules

### Vehicle

```
DRAFT -> ACTIVE
ACTIVE -> MAINTENANCE
ACTIVE -> SUSPENDED
ACTIVE -> ARCHIVED (only if archive preconditions pass)
MAINTENANCE -> ACTIVE
MAINTENANCE -> SUSPENDED
SUSPENDED -> ACTIVE
SUSPENDED -> MAINTENANCE
Any -> ARCHIVED (only if archive preconditions pass)
```

### Listing

```
DRAFT -> PENDING_APPROVAL (via submit)
PENDING_APPROVAL -> ACTIVE (via admin approve)
PENDING_APPROVAL -> DRAFT (via admin reject)
PENDING_APPROVAL -> ARCHIVED (via admin reject or host archive)
ACTIVE -> SUSPENDED (via admin suspend or vehicle coupling)
SUSPENDED -> ACTIVE (via admin reactivate)
DRAFT/PENDING_APPROVAL/ACTIVE/SUSPENDED -> ARCHIVED
```

## Coupling Rules

| Event | Action |
|---|---|
| Vehicle -> MAINTENANCE/SUSPENDED | All ACTIVE listings -> SUSPENDED |
| Vehicle -> ARCHIVED | All non-ARCHIVED listings -> ARCHIVED (if archive preconditions pass) |
| Listing -> ACTIVE | Vehicle must be ACTIVE |
| Admin approves listing | Generate 365 availability_calendar rows in same TX |
| Host deletes vehicle | Archive vehicle + all its listings (if preconditions pass) |

## Acceptance Criteria

- [ ] Host can create vehicle with default ACTIVE status
- [ ] Host can save vehicle as DRAFT
- [ ] Host can only manage own vehicles
- [ ] Vehicle archive fails if HELD/CONFIRMED/IN_PROGRESS bookings exist
- [ ] Vehicle archive: archive all non-ARCHIVED listings first, then archive vehicle, all in same transaction
- [ ] Host can create listing for own vehicle
- [ ] Double submit listing returns conflict
- [ ] Submit from non-DRAFT returns conflict
- [ ] Admin approval creates ACTIVE listing + 365 availability rows
- [ ] Admin approval fails if vehicle not ACTIVE
- [ ] One ACTIVE listing per vehicle enforced
- [ ] Vehicle suspend auto-suspends ACTIVE listing
- [ ] CONFIRMED bookings remain valid when listing suspended
- [ ] Vehicle status default ACTIVE
- [ ] DRAFT vehicles cannot be linked to ACTIVE listings

## Tests Required

- [ ] Unit: Vehicle state machine valid transitions
- [ ] Unit: Vehicle state machine invalid transitions rejected
- [ ] Unit: Listing state machine valid transitions
- [ ] Unit: Listing state machine invalid transitions rejected
- [ ] Unit: Vehicle archive preconditions
- [ ] Unit: One ACTIVE listing per vehicle check
- [ ] Integration: Host creates vehicle -> ACTIVE by default
- [ ] Integration: Host creates vehicle as DRAFT
- [ ] Integration: Host archives vehicle with active bookings -> 409
- [ ] Integration: Host archives vehicle -> cascades to listings
- [ ] Integration: Vehicle suspend -> listing auto-suspended
- [ ] Integration: Vehicle suspend -> confirmed booking remains valid
- [ ] Integration: Double submit listing -> 409
- [ ] Integration: Submit non-DRAFT listing -> 409
- [ ] Integration: Admin approves listing -> 365 availability rows created
- [ ] Integration: Admin approves listing with non-ACTIVE vehicle -> 409
- [ ] Security: Host cannot access another host's vehicle
- [ ] Security: Host cannot access another host's listing
- [ ] Security: Non-admin cannot access admin listing endpoints

## Notes

- Use `@Version` for optimistic locking on Listing entity.
- Use atomic UPDATE WHERE for listing submit (TX-09).
- Availability generation must be in the same transaction as approval.
- Vehicle/archive must archive all listings in the same transaction.
- Store encrypted plate/VIN using a simple encryption utility. For P0, AES with a secret key from environment is acceptable.
