-- V4__availability_calendar_indexes.sql
-- Phase 3 corrective: fix hold_token column type and add missing indexes.
-- Phase 3 has not yet generated any hold tokens, so all existing values are NULL.
-- This migration is safe to run on fresh Phase 3 databases.

-- Fix 1: Correct hold_token from VARCHAR(64) to UUID type.
-- Existing data is NULL (Phase 3 has no hold tokens yet), so conversion is safe.
ALTER TABLE availability_calendar
ALTER COLUMN hold_token TYPE UUID
USING CASE
    WHEN hold_token IS NULL OR hold_token = '' THEN NULL
    ELSE hold_token::UUID
END;

-- Fix 2: Add composite index for availability queries filtered by listing + date + status.
-- Covers: WHERE listing_id = ? AND available_date BETWEEN ? AND ? AND status = ?
CREATE INDEX idx_availability_listing_date_status
ON availability_calendar(listing_id, available_date, status);

-- Fix 3: Add partial index for hold expiration scheduler queries.
-- Covers: WHERE status = 'HOLD' AND hold_expires_at < NOW()
CREATE INDEX idx_availability_hold_expiry
ON availability_calendar(status, hold_expires_at)
WHERE status = 'HOLD';
