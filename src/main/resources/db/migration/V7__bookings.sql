-- V7__bookings.sql
-- Phase 5: Booking core tables.

CREATE TABLE bookings (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id                 UUID NOT NULL REFERENCES auth_users(id),
    host_id                     UUID NOT NULL REFERENCES auth_users(id),
    listing_id                  UUID NOT NULL REFERENCES listings(id),
    pickup_date                 DATE NOT NULL,
    return_date                 DATE NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'HELD',
    hold_token                  UUID,
    hold_expires_at             TIMESTAMPTZ,
    host_approval_expires_at    TIMESTAMPTZ,
    pickup_location             TEXT,
    return_location             TEXT,
    price_snapshot              JSONB NOT NULL,
    policy_snapshot             JSONB NOT NULL,
    cancellation_reason         VARCHAR(500),
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE bookings
ADD CONSTRAINT chk_bookings_status
CHECK (status IN (
    'HELD',
    'PENDING_HOST_APPROVAL',
    'CONFIRMED',
    'IN_PROGRESS',
    'COMPLETED',
    'CANCELLED',
    'REJECTED',
    'EXPIRED'
));

ALTER TABLE bookings
ADD CONSTRAINT chk_bookings_date_range
CHECK (pickup_date < return_date);

CREATE TABLE booking_extras (
    booking_id      UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    extra_id        UUID NOT NULL REFERENCES extras(id),
    quantity        INTEGER NOT NULL,
    price_snapshot  NUMERIC(12, 2) NOT NULL,
    PRIMARY KEY (booking_id, extra_id)
);

ALTER TABLE booking_extras
ADD CONSTRAINT chk_booking_extras_quantity
CHECK (quantity > 0);

CREATE TABLE idempotency_keys (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES auth_users(id),
    scope           VARCHAR(80) NOT NULL,
    key             VARCHAR(120) NOT NULL,
    request_hash    VARCHAR(128) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    response_status INTEGER,
    response_body   JSONB,
    locked_until    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE idempotency_keys
ADD CONSTRAINT chk_idempotency_status
CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'));

ALTER TABLE idempotency_keys
ADD CONSTRAINT uq_idempotency_scope_key
UNIQUE (user_id, scope, key);

CREATE INDEX idx_bookings_customer_period_status
    ON bookings(customer_id, pickup_date, return_date, status);

CREATE INDEX idx_bookings_listing_period_status
    ON bookings(listing_id, pickup_date, return_date, status);

CREATE INDEX idx_bookings_status_hold_expiry
    ON bookings(status, hold_expires_at)
    WHERE status = 'HELD';

CREATE INDEX idx_idempotency_status_locked_until
    ON idempotency_keys(status, locked_until);
