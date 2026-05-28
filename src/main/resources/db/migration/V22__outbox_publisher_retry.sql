ALTER TABLE outbox_events
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_error TEXT,
    ADD COLUMN sent_at TIMESTAMPTZ;

CREATE INDEX idx_outbox_events_publishable
    ON outbox_events(status, next_attempt_at, created_at);
