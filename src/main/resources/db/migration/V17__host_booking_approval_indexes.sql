CREATE INDEX IF NOT EXISTS idx_bookings_host_status_created
    ON bookings(host_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bookings_pending_host_approval_expiry
    ON bookings(status, host_approval_expires_at)
    WHERE status = 'PENDING_HOST_APPROVAL';
