CREATE TABLE vehicle_photos (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    file_id UUID NOT NULL REFERENCES files(id),
    display_order INT NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_vehicle_photos_vehicle_file ON vehicle_photos(vehicle_id, file_id);
CREATE INDEX idx_vehicle_photos_vehicle_order ON vehicle_photos(vehicle_id, display_order);
