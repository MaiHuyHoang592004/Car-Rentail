CREATE TABLE disputes (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    customer_id UUID NOT NULL REFERENCES auth_users(id),
    status VARCHAR(30) NOT NULL,
    reason TEXT NOT NULL,
    resolution_note TEXT,
    resolved_by UUID REFERENCES auth_users(id),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_disputes_booking_customer ON disputes(booking_id, customer_id);
CREATE INDEX idx_disputes_status_created_at ON disputes(status, created_at DESC);
