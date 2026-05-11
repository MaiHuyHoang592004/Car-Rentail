# Availability Rules

## Availability statuses
FREE — can be booked
HOLD — temporarily held for a booking (expires after 15 min)
BOOKED — confirmed/in-progress/completed booking
BLOCKED — manually blocked by host or admin

## Booking status → availability status mapping
HELD                  → HOLD
PENDING_HOST_APPROVAL → HOLD
CONFIRMED             → BOOKED
IN_PROGRESS           → BOOKED
COMPLETED             → BOOKED
CANCELLED             → FREE
REJECTED              → FREE
EXPIRED               → FREE

## Critical rules
- Missing availability row = listing is UNAVAILABLE (BR-08)
  Do NOT treat missing rows as FREE
- Lock order for availability rows: ORDER BY available_date ASC always
- Host cannot block dates with status HOLD or BOOKED
- Host can only unblock dates with status BLOCKED
- Public view: return FREE and BLOCKED only — never expose bookingId, holdToken,
  holdExpiresAt for HOLD/BOOKED rows
- Host view: return all statuses including bookingId and expiresAt for HOLD rows

## Availability generation
- When listing becomes ACTIVE: generate 365 rows starting from today (UTC)
- Must be in the same transaction as approveListing()
- Row count must equal 365 — throw exception if not

## Search date filter rule
When pickupDate and returnDate are provided:
Exclude listings that have ANY availability row in [pickupDate, returnDate)
with status IN ('HOLD', 'BOOKED').
Also exclude listings that are MISSING rows for any date in that range.
Use EXISTS subquery or NOT IN — never filter in application code.
