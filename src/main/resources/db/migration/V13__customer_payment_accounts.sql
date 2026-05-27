-- V13__customer_payment_accounts.sql
-- Phase 06 Slice 6C: provider account mapping for external payment providers.

CREATE TABLE customer_payment_accounts (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id                 UUID NOT NULL REFERENCES auth_users(id),
    provider                VARCHAR(40) NOT NULL,
    provider_account_id     VARCHAR(120) NOT NULL,
    provider_customer_ref   VARCHAR(120),
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    metadata                JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE customer_payment_accounts
ADD CONSTRAINT uq_customer_payment_accounts_user_provider
UNIQUE (user_id, provider);

ALTER TABLE customer_payment_accounts
ADD CONSTRAINT chk_customer_payment_accounts_provider
CHECK (provider IN ('VIETQR_MANUAL', 'COREBANK', 'STUB'));

CREATE INDEX idx_customer_payment_accounts_active_provider
    ON customer_payment_accounts(user_id, provider, active);
