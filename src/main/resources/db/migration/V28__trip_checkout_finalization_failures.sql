CREATE TABLE trip_checkout_finalization_failures (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    trip_record_id UUID NOT NULL REFERENCES trip_records(id),
    actor_user_id UUID NOT NULL REFERENCES auth_users(id),
    captured_payment_id UUID NULL REFERENCES booking_payments(id),
    check_in_odometer INT NOT NULL,
    check_out_odometer INT NOT NULL,
    check_out_fuel_level INT NOT NULL,
    check_out_note TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    failure_code VARCHAR(80),
    failure_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_trip_checkout_failures_booking_status
    ON trip_checkout_finalization_failures(booking_id, status, created_at DESC);
