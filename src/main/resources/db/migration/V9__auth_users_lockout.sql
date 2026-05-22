-- V9__auth_users_lockout.sql
-- I17: Account lockout — track consecutive failed login attempts and lock_until timestamp.

ALTER TABLE auth_users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN lock_until TIMESTAMPTZ;
