# Phase 04 — Search + Availability

## Goal

Implement public listing search with filters, pagination, availability calendar views, and host block/unblock functionality.

## Must Implement

- [ ] `GET /api/v1/listings`: public search with filters
- [ ] Filters: city, category[], pickupDate, returnDate, minPrice, maxPrice, seats, transmission, fuelType
- [ ] Pagination with page/size params (default page=0, size=20, max size=100)
- [ ] Only ACTIVE listings returned (BR-01)
- [ ] Search with dates: exclude listings with HOLD/BOOKED availability for requested dates
- [ ] Missing availability row = unavailable (BR-08)
- [ ] `ratingAverage` = null in P0 (FR-AVL-10)
- [ ] `GET /api/v1/listings/{id}`: public listing detail
- [ ] `GET /api/v1/listings/{id}/availability?from=&to=`: public availability view
- [ ] Public view: only shows FREE/BLOCKED dates (hides HOLD/BOOKED details)
- [ ] `GET /api/v1/host/listings/{id}/availability?from=&to=`: host full view
- [ ] Host view: shows all statuses (FREE, HOLD, BOOKED, BLOCKED)
- [ ] `POST /api/v1/host/listings/{id}/availability/block`: block FREE dates
- [ ] `POST /api/v1/host/listings/{id}/availability/unblock`: unblock BLOCKED dates
- [ ] Block conflict: cannot block dates that are HOLD or BOOKED
- [ ] Unblock: only BLOCKED dates can be unblocked
- [ ] Pagination helpers in `common/pagination/`
- [ ] `ListingSearchRepository` with custom JPQL or native query for availability filtering

## Must Not Implement

- [ ] Payment
- [ ] Booking creation (but search should filter by availability)
- [ ] Driver verification
- [ ] Listing photos in search results
- [ ] Reviews / ratings in search results
- [ ] MinIO file URLs in search

## Files/Modules Expected

```
com.rentflow.listing/
├── controller/
│   ├── ListingSearchController.java
│   └── ListingDetailController.java
├── service/
│   └── ListingSearchService.java
├── repository/
│   └── ListingSearchRepository.java
└── dto/
    ├── ListingSearchRequest.java
    ├── ListingSearchResponse.java
    └── ListingDetailResponse.java

com.rentflow.availability/
├── controller/
│   ├── AvailabilityController.java
│   └── HostAvailabilityController.java
├── service/
│   └── AvailabilityService.java
├── entity/
│   └── AvailabilityCalendar.java
├── repository/
│   └── AvailabilityRepository.java
└── dto/
    ├── AvailabilityResponse.java
    ├── BlockDatesRequest.java
    └── UnblockDatesRequest.java
```

## API Contracts

### GET /api/v1/listings

Query params:

| Param | Type | Notes |
|---|---|---|
| city | string | exact or prefix match |
| category | string[] | SEDAN, SUV, HATCHBACK, MPV, PICKUP, LUXURY, VAN |
| pickupDate | date | required with returnDate |
| returnDate | date | must be after pickupDate |
| minPrice | decimal | >= 0 |
| maxPrice | decimal | >= minPrice |
| seats | int | minimum seats |
| transmission | string | AUTO, MANUAL |
| fuelType | string | GASOLINE, DIESEL, EV, HYBRID |
| page | int | default 0 |
| size | int | default 20, max 100 |

Response:

```json
{
  "content": [
    {
      "id": "listing-id",
      "title": "Toyota Vios 2022",
      "city": "Hanoi",
      "category": "SEDAN",
      "basePricePerDay": 700000,
      "currency": "VND",
      "seats": 5,
      "transmission": "AUTO",
      "fuelType": "GASOLINE",
      "coverPhotoUrl": null,
      "ratingAverage": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

### GET /api/v1/listings/{id}/availability

Query: `from=2026-06-01&to=2026-06-30`

Response (public - no HOLD/BOOKED details):

```json
{
  "listingId": "uuid",
  "from": "2026-06-01",
  "to": "2026-06-30",
  "dates": [
    { "date": "2026-06-01", "status": "FREE" },
    { "date": "2026-06-02", "status": "BLOCKED" }
  ]
}
```

### GET /api/v1/host/listings/{id}/availability

Response (host - all statuses):

```json
{
  "listingId": "uuid",
  "from": "2026-06-01",
  "to": "2026-06-30",
  "dates": [
    { "date": "2026-06-01", "status": "FREE" },
    { "date": "2026-06-02", "status": "HOLD", "bookingId": "uuid", "expiresAt": "..." },
    { "date": "2026-06-03", "status": "BOOKED", "bookingId": "uuid" },
    { "date": "2026-06-04", "status": "BLOCKED" }
  ]
}
```

### POST /api/v1/host/listings/{id}/availability/block

Request:

```json
{
  "dates": ["2026-06-10", "2026-06-11", "2026-06-12"]
}
```

### POST /api/v1/host/listings/{id}/availability/unblock

Request:

```json
{
  "dates": ["2026-06-10", "2026-06-11"]
}
```

## Availability Rules

| Rule | Description |
|---|---|
| One row per listing/date | Primary key is `(listing_id, available_date)` |
| Rows generated in advance | When listing becomes ACTIVE, generate 365 rows |
| Missing row = unavailable | Booking/search must not treat missing rows as FREE |
| Host block conflict | Host cannot block dates that are HOLD or BOOKED |

## Booking-to-Availability Mapping

| Booking Status | Availability Status |
|---|---|
| HELD | HOLD |
| PENDING_HOST_APPROVAL | HOLD |
| CONFIRMED | BOOKED |
| IN_PROGRESS | BOOKED |
| COMPLETED | BOOKED |
| CANCELLED before check-in | FREE |
| REJECTED | FREE |
| EXPIRED | FREE |

## Acceptance Criteria

- [ ] Public search returns only ACTIVE listings
- [ ] Search with dates excludes listings with HOLD/BOOKED dates in range
- [ ] Missing availability row excludes listing from search results
- [ ] Pagination works correctly
- [ ] All filters work correctly
- [ ] Guest can search listings (no auth required)
- [ ] Customer can search listings
- [ ] Host can view full availability calendar for own listing
- [ ] Host sees all statuses in full view (including HOLD/BOOKED)
- [ ] Public availability view hides HOLD/BOOKED booking details
- [ ] Host can block FREE dates
- [ ] Host cannot block HOLD or BOOKED dates
- [ ] Host can unblock BLOCKED dates
- [ ] Host cannot unblock FREE/HOLD/BOOKED dates

## Tests Required

- [ ] Unit: Availability date range calculation
- [ ] Unit: Search filter combinations
- [ ] Integration: search returns only ACTIVE listings
- [ ] Integration: search with dates excludes unavailable listings
- [ ] Integration: search excludes listings with missing availability
- [ ] Integration: pagination works correctly
- [ ] Integration: public availability hides HOLD/BOOKED details
- [ ] Integration: host availability shows all statuses
- [ ] Integration: block FREE dates succeeds
- [ ] Integration: block HOLD dates -> 409
- [ ] Integration: block BOOKED dates -> 409
- [ ] Integration: unblock BLOCKED dates succeeds
- [ ] Integration: unblock FREE dates -> 409
- [ ] Security: guest can search (no auth)
- [ ] Security: guest cannot access host availability view

## Notes

- Use Spring Data JPA `Specification` or `Example` for dynamic search queries.
- For availability date filtering, use a subquery or JOIN with availability_calendar.
- Host availability controller must check `listing.host_id == authUserId`.
- Block/unblock should use batch update for performance.
- Availability response dates must be sorted ascending.
