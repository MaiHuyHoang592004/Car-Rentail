ALTER TABLE outbox_events
    ADD COLUMN processing_started_at TIMESTAMPTZ,
    ADD COLUMN claimed_by VARCHAR(120);

CREATE INDEX idx_outbox_events_stale_processing
    ON outbox_events(status, processing_started_at)
    WHERE status = 'PROCESSING';
