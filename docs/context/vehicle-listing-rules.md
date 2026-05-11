# Vehicle & Listing Rules

## Vehicle State Machine (allowed transitions)

```
DRAFT → ACTIVE
ACTIVE → MAINTENANCE, SUSPENDED, ARCHIVED
MAINTENANCE → ACTIVE, SUSPENDED
SUSPENDED → ACTIVE, MAINTENANCE
Any → ARCHIVED (if preconditions pass)
```

## Listing State Machine (allowed transitions)

```
DRAFT → PENDING_APPROVAL
PENDING_APPROVAL → ACTIVE, DRAFT (via reject)
ACTIVE → SUSPENDED
SUSPENDED → ACTIVE
DRAFT/PENDING_APPROVAL/ACTIVE/SUSPENDED → ARCHIVED
```

## Vehicle Archive Preconditions (BR-36)

Vehicle can be archived **only** when:
- All listings for the vehicle are `ARCHIVED`
- No bookings with status `HELD`, `CONFIRMED`, or `IN_PROGRESS` for any listing on this vehicle

Otherwise: 409 VEHICLE_ARCHIVE_NOT_ALLOWED

## Archive Cascade (BR-56)

When archiving a vehicle:
1. Archive all non-ARCHIVED listings **first**
2. Then archive the vehicle
3. Both operations in the **same transaction**

If listing archive fails → entire transaction rolls back.

## Vehicle → Listing Coupling (BR-37)

When vehicle becomes `MAINTENANCE` or `SUSPENDED`:
- All `ACTIVE` listings for that vehicle → `SUSPENDED`
- `CONFIRMED`/`IN_PROGRESS` bookings remain unchanged

## One ACTIVE Listing Per Vehicle

- DB constraint: `CREATE UNIQUE INDEX uq_listings_one_active_per_vehicle ON listings(vehicle_id) WHERE status = 'ACTIVE'`
- On duplicate: catch `DataIntegrityViolationException` → 409 ONE_ACTIVE_LISTING_PER_VEHICLE
- Never let `DataIntegrityViolationException` propagate as 500

## Vehicle Requirements

- Vehicle must be `ACTIVE` before its listing can be `ACTIVE`
- New vehicles default to `ACTIVE` unless host explicitly saves as `DRAFT`
- `DRAFT` vehicles cannot be linked to `ACTIVE` listings
