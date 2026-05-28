CREATE TABLE trip_records (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id),
    customer_id UUID NOT NULL REFERENCES auth_users(id),
    check_in_at TIMESTAMPTZ NOT NULL,
    check_out_at TIMESTAMPTZ,
    check_in_odometer INT NOT NULL,
    check_out_odometer INT,
    check_in_fuel_level INT NOT NULL CHECK (check_in_fuel_level >= 0 AND check_in_fuel_level <= 100),
    check_out_fuel_level INT CHECK (check_out_fuel_level >= 0 AND check_out_fuel_level <= 100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_trip_records_customer ON trip_records(customer_id);
