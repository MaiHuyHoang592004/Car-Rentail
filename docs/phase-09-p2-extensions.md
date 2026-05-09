# Phase 09 — P2 Extensions

## Goal

Implement advanced portfolio extensions: files, trip lifecycle, reviews, disputes, reports, payouts, outbox scheduler, and CI pipeline.

## Must Implement

### Files and Listing Photos (P1/P2)

- [ ] `File` entity + repository
- [ ] `ListingPhoto` entity + repository
- [ ] `FileService`: upload metadata, delete metadata (soft delete)
- [ ] MinIO integration: upload, download, signed URLs
- [ ] `POST /api/v1/host/listings/{id}/photos` — add photo to listing
- [ ] `POST /api/v1/users/me/driver-license` with file upload
- [ ] Signed URL generation with 10-minute TTL
- [ ] File permission rules: private by default
- [ ] Listing photos become public only after listing is ACTIVE
- [ ] Allowed MIME types by file purpose
- [ ] File size limits: 10MB photos, 20MB documents
- [ ] `files` table tracks metadata: bucket, object_key, content_type, size, checksum

### Trip Lifecycle (P2)

- [ ] `POST /api/v1/bookings/{id}/check-in` — customer checks in
- [ ] `POST /api/v1/bookings/{id}/check-out` — customer checks out
- [ ] Check-in validation: booking CONFIRMED, listing ACTIVE, vehicle ACTIVE
- [ ] Check-out: triggers payment capture
- [ ] Odometer reading at check-in and check-out
- [ ] Fuel level tracking
- [ ] Trip photos upload
- [ ] Booking status: CONFIRMED -> IN_PROGRESS (check-in), IN_PROGRESS -> COMPLETED (check-out)

### Reviews (P2)

- [ ] `Review` entity + repository
- [ ] `POST /api/v1/bookings/{id}/review` — customer reviews completed booking
- [ ] Rating: 1-5 stars
- [ ] Review content: text
- [ ] One review per booking per reviewer
- [ ] Only COMPLETED bookings can be reviewed
- [ ] Listing average rating calculation
- [ ] `GET /api/v1/listings/{id}/reviews` — listing reviews

### Disputes (P2)

- [ ] `Dispute` entity + repository
- [ ] `POST /api/v1/bookings/{id}/dispute` — customer raises dispute
- [ ] `GET /api/v1/admin/disputes?status=&page=&size=`
- [ ] `POST /api/v1/admin/disputes/{id}/resolve` — admin resolves
- [ ] Dispute statuses: OPEN, UNDER_REVIEW, RESOLVED
- [ ] Refund can be processed through dispute resolution

### Reports (P2)

- [ ] `GET /api/v1/admin/reports/revenue?from=&to=` — admin revenue report
- [ ] `GET /api/v1/host/reports/earnings?from=&to=` — host earnings report
- [ ] Booking utilization report
- [ ] Revenue by city/listing

### Payouts (P2)

- [ ] Payout calculation: host earnings = total captured - platform fee
- [ ] Platform fee: configurable percentage (e.g., 15%)
- [ ] Payout schedule: weekly/monthly
- [ ] Payout status tracking

### Outbox Scheduler (P2)

- [ ] `OutboxEvent` entity + repository
- [ ] Outbox scheduler: poll NEW events
- [ ] Retry with exponential backoff
- [ ] Max retry count: 5
- [ ] Event types: BookingConfirmed, BookingCancelled, PaymentCaptured, etc.
- [ ] Kafka integration (optional)

### CI Pipeline

- [ ] GitHub Actions workflow
- [ ] Run tests on PR
- [ ] Run tests on push to main
- [ ] Build Docker image
- [ ] Publish to container registry

### Observability (P2)

- [ ] Spring Boot Actuator
- [ ] Custom metrics
- [ ] Structured logging (JSON format)

## Must Not Implement

- [ ] Real payment gateway (stub remains)
- [ ] Microservices
- [ ] Kubernetes (Docker Compose is sufficient for portfolio)

## Files/Modules Expected

```
com.rentflow.file/
├── controller/
│   └── FileController.java
├── service/
│   └── FileService.java
├── entity/
│   ├── FileMetadata.java
│   └── ListingPhoto.java
├── repository/
│   ├── FileMetadataRepository.java
│   └── ListingPhotoRepository.java
└── dto/
    ├── UploadFileResponse.java
    └── AddListingPhotoRequest.java

com.rentflow.trip/
├── controller/
│   └── TripController.java
├── service/
│   └── TripService.java
├── entity/
│   └── TripRecord.java
└── dto/
    ├── CheckInRequest.java
    ├── CheckOutRequest.java
    └── TripRecordResponse.java

com.rentflow.review/
├── controller/
│   └── ReviewController.java
├── service/
│   └── ReviewService.java
├── entity/
│   └── Review.java
└── repository/
    └── ReviewRepository.java

com.rentflow.dispute/
├── controller/
│   ├── DisputeController.java
│   └── AdminDisputeController.java
├── service/
│   └── DisputeService.java
├── entity/
│   └── Dispute.java
└── repository/
    └── DisputeRepository.java

com.rentflow.report/
├── controller/
│   ├── AdminReportController.java
│   └── HostReportController.java
├── service/
│   └── ReportService.java
└── dto/
    ├── RevenueReportResponse.java
    └── EarningsReportResponse.java

com.rentflow.outbox/
├── service/
│   └── OutboxPublisher.java   (scheduler)
└── (entity/repository already in phase 7)

.github/
└── workflows/
    └── ci.yml
```

## Acceptance Criteria

- [ ] Files uploaded to MinIO with metadata in DB
- [ ] Signed URLs generated for file access
- [ ] Listing photos visible in search after ACTIVE
- [ ] Driver license document upload works
- [ ] Check-in: booking CONFIRMED -> IN_PROGRESS
- [ ] Check-out: booking IN_PROGRESS -> COMPLETED, payment CAPTURED
- [ ] Reviews visible on listing
- [ ] Review rating affects listing average
- [ ] Customer can raise dispute on completed booking
- [ ] Admin can resolve dispute
- [ ] Admin revenue report works
- [ ] Host earnings report works
- [ ] Outbox events published and retried
- [ ] CI pipeline runs tests automatically
- [ ] Docker image built and published

## Tests Required

- [ ] Integration: File upload metadata created
- [ ] Integration: Signed URL generated
- [ ] Integration: Check-in validation
- [ ] Integration: Check-out triggers capture
- [ ] Integration: Review created
- [ ] Integration: Duplicate review rejected
- [ ] Integration: Dispute raised
- [ ] Integration: Dispute resolved
- [ ] Integration: Revenue report generated
- [ ] Integration: Outbox publisher retries
- [ ] CI: Tests pass on GitHub Actions

## Notes

- P2 features are for portfolio enhancement. They are not required for the core proof.
- The concurrent booking test (Phase 5) is still the most important demo.
- Focus on quality over quantity. A well-implemented P2 feature is better than 10 half-implemented ones.
- Keep the outbox pattern simple — don't over-engineer the event publishing.
- For CI, GitHub Actions is free and sufficient for portfolio projects.
