ALTER TABLE files
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE disputes
    ADD COLUMN category VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN context TEXT,
    ADD COLUMN refund_action VARCHAR(40),
    ADD COLUMN refund_payment_id UUID,
    ADD COLUMN refund_amount NUMERIC(12,2);

CREATE TABLE dispute_attachments (
    id UUID PRIMARY KEY,
    dispute_id UUID NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,
    file_id UUID NOT NULL REFERENCES files(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_dispute_attachments_dispute_file
    ON dispute_attachments(dispute_id, file_id);
CREATE INDEX idx_dispute_attachments_file
    ON dispute_attachments(file_id);

ALTER TABLE listings
    ADD COLUMN suspension_reason TEXT,
    ADD COLUMN suspension_source VARCHAR(40),
    ADD COLUMN suspension_until TIMESTAMPTZ,
    ADD COLUMN rejected_reason TEXT,
    ADD COLUMN rejected_at TIMESTAMPTZ;
