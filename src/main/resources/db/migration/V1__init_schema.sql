-- V1__init_schema.sql
-- Phase 1: Auth + User tables only.
-- Subsequent phases add their own migrations.
-- V2: vehicles, listings, extras
-- V3: availability_calendar
-- V4: bookings, booking_extras, idempotency_keys
-- V5: booking_payments, payment_transactions
-- V6: driver_verifications, notifications, audit_logs, outbox_events, booking_timeline, files, listing_photos

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- auth_users
CREATE TABLE auth_users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(120) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified  BOOLEAN NOT NULL DEFAULT false,
    last_login_at   TIMESTAMPTZ NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE auth_users
ADD CONSTRAINT chk_auth_users_status
CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'));

-- user_roles
CREATE TABLE user_roles (
    user_id     UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role)
);

ALTER TABLE user_roles
ADD CONSTRAINT chk_user_roles_role
CHECK (role IN ('CUSTOMER', 'HOST', 'ADMIN'));

-- refresh_tokens
CREATE TABLE refresh_tokens (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    token_hash          VARCHAR(255) UNIQUE NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ NULL,
    replaced_by_token_id UUID NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at);

-- user_profiles
CREATE TABLE user_profiles (
    user_id                         UUID PRIMARY KEY REFERENCES auth_users(id) ON DELETE CASCADE,
    full_name                       VARCHAR(120) NOT NULL,
    phone                           VARCHAR(30) NULL,
    date_of_birth                   DATE NULL,
    address_line                    TEXT NULL,
    driver_verification_status      VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE user_profiles
ADD CONSTRAINT chk_user_profiles_dv_status
CHECK (driver_verification_status IN ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'));

CREATE INDEX idx_auth_users_status_created ON auth_users(status, created_at DESC);
CREATE INDEX idx_user_profiles_dv_status ON user_profiles(driver_verification_status);
