-- V11__email_verification_tokens.sql
-- I14: email_verification_tokens for email verification flow.

CREATE TABLE email_verification_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens(user_id);
CREATE INDEX idx_email_verification_tokens_expires_at ON email_verification_tokens(expires_at);
