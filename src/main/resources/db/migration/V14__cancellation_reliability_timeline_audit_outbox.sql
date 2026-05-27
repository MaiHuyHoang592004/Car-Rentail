ALTER TABLE booking_payments
    ADD COLUMN void_retry_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN void_retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN void_retry_next_at TIMESTAMPTZ,
    ADD COLUMN void_retry_last_error TEXT;

CREATE INDEX idx_booking_payments_void_retry
    ON booking_payments(void_retry_required, void_retry_next_at, void_retry_count)
    WHERE void_retry_required = TRUE;

CREATE TABLE booking_timeline_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    event_type VARCHAR(80) NOT NULL,
    actor_user_id UUID,
    actor_type VARCHAR(40) NOT NULL,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_booking_timeline_entries_booking_created
    ON booking_timeline_entries(booking_id, created_at);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    actor_user_id UUID,
    actor_type VARCHAR(40) NOT NULL,
    action VARCHAR(120) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id UUID,
    status VARCHAR(40) NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_created_at
    ON audit_logs(created_at);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID,
    event_type VARCHAR(120) NOT NULL,
    payload JSONB,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_events_status_created
    ON outbox_events(status, created_at);
