-- V10__password_reset_tokens.sql
-- I13: password_reset_tokens for forgot/reset password flow.

CREATE TABLE password_reset_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);
