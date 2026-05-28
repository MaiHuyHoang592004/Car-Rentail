ALTER TABLE listings
    ADD COLUMN average_rating NUMERIC(3,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN review_count INT NOT NULL DEFAULT 0;

CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    listing_id UUID NOT NULL REFERENCES listings(id),
    reviewer_id UUID NOT NULL REFERENCES auth_users(id),
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    content TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_reviews_booking_reviewer ON reviews(booking_id, reviewer_id);
CREATE INDEX idx_reviews_listing_created_at ON reviews(listing_id, created_at DESC);
