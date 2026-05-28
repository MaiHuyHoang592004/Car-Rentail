CREATE TABLE driver_verifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    license_number_encrypted VARCHAR(1000) NOT NULL,
    license_number_hash VARCHAR(128) NOT NULL,
    license_expiry_date DATE NOT NULL,
    document_file_id UUID,
    review_reason VARCHAR(500),
    reviewed_by UUID REFERENCES auth_users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE driver_verifications
    ADD CONSTRAINT chk_driver_verifications_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'));

CREATE INDEX idx_driver_verifications_customer_status_created
    ON driver_verifications(customer_id, status, created_at DESC);

CREATE UNIQUE INDEX uq_driver_verification_active
    ON driver_verifications(customer_id)
    WHERE status IN ('PENDING', 'APPROVED');
