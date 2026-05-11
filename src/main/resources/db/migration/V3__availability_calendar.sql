-- V3__availability_calendar.sql
-- Phase 3: Availability calendar for approved listings.

CREATE TABLE availability_calendar (
    listing_id                  UUID NOT NULL,
    available_date              DATE NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'FREE',
    hold_token                  VARCHAR(64),
    hold_expires_at             TIMESTAMPTZ,
    booking_id                  UUID,
    version                     INTEGER NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (listing_id, available_date)
);

ALTER TABLE availability_calendar
ADD CONSTRAINT fk_availability_listing
FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE;

ALTER TABLE availability_calendar
ADD CONSTRAINT chk_availability_status CHECK (status IN ('FREE', 'HOLD', 'BOOKED', 'BLOCKED'));

CREATE INDEX idx_availability_listing_date ON availability_calendar(listing_id, available_date);
CREATE INDEX idx_availability_status_date ON availability_calendar(status, available_date) WHERE status != 'BOOKED';
