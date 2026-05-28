CREATE TABLE files (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES auth_users(id),
    purpose VARCHAR(40) NOT NULL,
    bucket VARCHAR(120) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    checksum VARCHAR(128),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_files_bucket_object_key ON files(bucket, object_key);
CREATE INDEX idx_files_owner_status ON files(owner_user_id, status);
CREATE INDEX idx_files_purpose_visibility ON files(purpose, visibility);

CREATE TABLE listing_photos (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listings(id),
    file_id UUID NOT NULL REFERENCES files(id),
    display_order INT NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_listing_photos_listing_file ON listing_photos(listing_id, file_id);
CREATE INDEX idx_listing_photos_listing_order ON listing_photos(listing_id, display_order);
