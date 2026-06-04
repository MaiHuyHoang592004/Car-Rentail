# RentFlow Gap Remediation Plan

This file is the tracking source for product, UX, backend, and security gaps found during the May 2026 review. Keep IDs stable so later implementation turns do not lose context.

Slice 5 and Slice 6 are one committed delivery scope: do not pause, defer, or end the task until every approved item in both slices is implemented, tested, and summarized, unless the user explicitly stops or reprioritizes the work.

## Status Legend

- `todo`: not started.
- `in_progress`: currently being implemented.
- `done`: implemented and validated.
- `deferred`: intentionally postponed.

## Existing Audit Gaps

| ID | Severity | Area | Current Status | Planned Slice | Summary |
|---|---:|---|---|---|---|
| GAP-001 | P1 | fullstack | partial | Slice 3 | Vehicle photos now exist, but current flow is metadata-first and not production binary upload/storage. |
| GAP-002 | P3 | frontend | done | Done | Listing create UI filters to ACTIVE vehicles. |
| GAP-003 | P2 | frontend | done | Slice 4 | Vehicle archive confirmation lists affected listings via archive preview API. |
| GAP-004 | P3 | backend | todo | Slice 7 | Plate/VIN decrypt failure has no explicit user-facing state. |
| GAP-005 | P1 | fullstack | done | Slice 4 | Suspended listing now persists source/reason metadata for admin and vehicle-driven suspensions. |
| GAP-006 | P2 | fullstack | done | Slice 4 | Listing edit is draft-only; UI guides archive -> reactivate -> edit -> resubmit. |
| GAP-007 | P1 | fullstack | done | Slice 4 | Host can manage listing extras via API/UI (soft delete). |
| GAP-008 | P3 | fullstack | deferred | Slice 7 | Currency is effectively VND-only. |
| GAP-009 | P2 | fullstack | done | Slice 4 | Availability supports block/unblock range API and host UI. |
| GAP-010 | P2 | frontend | done | Slice 4 | HOLD dates show booking link and expiry in host availability view. |
| GAP-011 | P2 | backend | done | Slice 4 | Host can extend availability beyond initial 365-day generation. |
| GAP-012 | P1 | fullstack | done | Slice 4 | Host booking workspace includes approval countdown and actions. |
| GAP-013 | P1 | fullstack | done | Slice 4 | Host reject booking persists reason and customer sees it. |
| GAP-014 | P2 | backend | done | Slice 4 | Host bookings can be filtered by listingId. |
| GAP-015 | P2 | backend | done | Slice 4 | Host overview report includes utilization metrics. |
| GAP-016 | P2 | backend | done | Slice 5 | Public search returns persisted listing ratingAverage when reviews exist. |
| GAP-017 | P2 | fullstack | done | Slice 5 | Public search supports backed NEWEST, PRICE_ASC, and PRICE_DESC sort options. |
| GAP-018 | P2 | fullstack | done | Slice 5 | Public search supports title/make/model text query. |
| GAP-019 | P3 | frontend | done | Done | Frontend validates invalid date range before search. |
| GAP-020 | P3 | frontend | done | Done | Booking detail has pay-now action. |
| GAP-021 | P3 | frontend | done | Done | Payment bank selection UI exists. |
| GAP-022 | P3 | frontend | done | Done | Booking create shows price summary before submit. |
| GAP-023 | P2 | fullstack | done | Slice 5 | Customer booking create shows active-booking overlap advisory before submit. |
| GAP-024 | P2 | fullstack | done | Slice 5 | Extras quantity is first-class frontend input and backend validates 1..5. |
| GAP-025 | P1 | fullstack | done | Slice 4 | Customer booking detail shows rejection reason when available. |
| GAP-026 | P2 | fullstack | done | Slice 5 | Booking detail/cancel flow shows backend cancellation refund preview. |
| GAP-027 | P2 | fullstack | deferred | Slice 7 | Real reschedule stays deferred; customer UI guides cancel-and-rebook instead. |
| GAP-028 | P3 | frontend | done | Done | Payment detail UI exists. |
| GAP-029 | P2 | frontend | done | Slice 5 | Completed booking shows review CTA when eligible and not submitted. |
| GAP-030 | P2 | fullstack | done | Slice 5 | Dispute form supports category, context, and private finalized attachments. |
| GAP-031 | P2 | fullstack | done | Slice 6 | Admin listing detail includes host, vehicle risk, active-booking, and moderation context. |
| GAP-032 | P2 | backend | done | Slice 6 | Listing rejection persists reason and creates host rejection notification. |
| GAP-033 | P2 | fullstack | done | Slice 6 | Admin listing suspension persists reason, source, and optional duration. |
| GAP-034 | P3 | frontend | deferred | Slice 7 | Admin listing bulk actions are missing. |
| GAP-035 | P1 | fullstack | done | Slice 6 | Admin can suspend and reactivate users with self-suspend guard. |
| GAP-036 | P2 | fullstack | done | Slice 6 | Admin user detail API exposes customer/host booking drill-down. |
| GAP-037 | P3 | admin | deferred | Slice 7 | Admin impersonation/view-as is missing. |
| GAP-038 | P2 | frontend | done | Slice 6 | Admin driver verification response/UI includes signed document preview URL. |
| GAP-039 | P2 | frontend | done | Slice 6 | Driver verification response/UI includes pending age and SLA breach flag. |
| GAP-040 | P1 | fullstack | done | Slice 6 | Admin dispute resolution can invoke refund action through payment service. |
| GAP-041 | P2 | fullstack | done | Slice 6 | Admin dispute detail includes booking, payment, and timeline context. |

## Additional Findings

| ID | Severity | Area | Current Status | Planned Slice | Summary |
|---|---:|---|---|---|---|
| GAP-042 | P0 | backend | done | Slice 1 | Vehicle status propagation can turn DRAFT/PENDING listing into SUSPENDED, then host resume can bypass admin approval. |
| GAP-043 | P0 | backend | done | Slice 1 | Manual payment capture/void/refund endpoints allow host mutation outside business lifecycle. |
| GAP-044 | P1 | frontend | done | Slice 2 | Public home featured listings calls host API with guest auth. |
| GAP-045 | P1 | frontend | done | Slice 2 | Booking page drops pickup/return dates selected on listing detail. |
| GAP-046 | P1 | fullstack | done | Slice 3 | Listing photos seeded from vehicle photos can become stale; prefer lazy fallback to vehicle photos. |
| GAP-047 | P1 | backend | done | Slice 3 | Listing photo primary uniqueness is not enforced in service. |
| GAP-048 | P2 | frontend | done | Slice 2 | Listing detail shows fake rating and host trust signal not backed by API. |

## Implementation Notes

- P0/P1 fixes should be implemented before host/admin/customer expansion work.
- Manual payment mutation is admin-only; host/customer operations must go through domain flows such as checkout, cancel, and host approval/reject.
- Vehicle photos are the base asset. Listing photos are explicit marketing overrides; public listing APIs should lazy-fallback to vehicle photos when listing photos are absent.
- Current photo implementation persists metadata and signed URL records. It is not a complete object upload/storage implementation.
