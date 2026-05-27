-- V12__payment_slice_6a.sql
-- Phase 06 Slice 6A: payment persistence groundwork + bank catalog seed.

CREATE TABLE payment_banks (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code            VARCHAR(40) NOT NULL,
    bin             VARCHAR(20),
    short_name      VARCHAR(80) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    logo_url        TEXT,
    country_code    VARCHAR(2) NOT NULL DEFAULT 'VN',
    payment_method  VARCHAR(40) NOT NULL,
    provider        VARCHAR(40) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    display_order   INTEGER NOT NULL DEFAULT 0,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE payment_banks
ADD CONSTRAINT uq_payment_banks_code
UNIQUE (code);

ALTER TABLE payment_banks
ADD CONSTRAINT chk_payment_banks_payment_method
CHECK (payment_method IN ('BANK_TRANSFER_QR', 'COREBANK_TRANSFER', 'STUB'));

ALTER TABLE payment_banks
ADD CONSTRAINT chk_payment_banks_provider
CHECK (provider IN ('VIETQR_MANUAL', 'COREBANK', 'STUB'));

CREATE TABLE booking_payments (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_id                  UUID NOT NULL REFERENCES bookings(id),
    selected_bank_id            UUID REFERENCES payment_banks(id),
    payment_method              VARCHAR(40) NOT NULL,
    provider                    VARCHAR(40) NOT NULL,
    status                      VARCHAR(40) NOT NULL,
    authorized_amount           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    captured_amount             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    refunded_amount             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency                    VARCHAR(3) NOT NULL DEFAULT 'VND',
    external_order_ref          VARCHAR(128),
    provider_payment_order_id   VARCHAR(120),
    provider_hold_id            VARCHAR(120),
    provider_status             VARCHAR(80),
    provider_metadata           JSONB,
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE booking_payments
ADD CONSTRAINT uq_booking_payments_booking_id
UNIQUE (booking_id);

ALTER TABLE booking_payments
ADD CONSTRAINT uq_booking_payments_external_order_ref
UNIQUE (external_order_ref);

ALTER TABLE booking_payments
ADD CONSTRAINT chk_booking_payments_payment_method
CHECK (payment_method IN ('BANK_TRANSFER_QR', 'COREBANK_TRANSFER', 'STUB'));

ALTER TABLE booking_payments
ADD CONSTRAINT chk_booking_payments_provider
CHECK (provider IN ('VIETQR_MANUAL', 'COREBANK', 'STUB'));

ALTER TABLE booking_payments
ADD CONSTRAINT chk_booking_payments_status
CHECK (status IN (
    'UNPAID',
    'PENDING_TRANSFER',
    'AUTHORIZED',
    'CAPTURED',
    'PARTIALLY_REFUNDED',
    'REFUNDED',
    'VOIDED',
    'FAILED',
    'RECONCILIATION_REQUIRED'
));

ALTER TABLE booking_payments
ADD CONSTRAINT chk_booking_payments_amounts
CHECK (
    authorized_amount >= 0
    AND captured_amount >= 0
    AND refunded_amount >= 0
    AND captured_amount <= authorized_amount
    AND refunded_amount <= captured_amount
);

CREATE TABLE payment_transactions (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_payment_id      UUID NOT NULL REFERENCES booking_payments(id),
    booking_id              UUID NOT NULL REFERENCES bookings(id),
    type                    VARCHAR(20) NOT NULL,
    status                  VARCHAR(40) NOT NULL,
    amount                  NUMERIC(12, 2) NOT NULL,
    currency                VARCHAR(3) NOT NULL DEFAULT 'VND',
    provider                VARCHAR(40) NOT NULL,
    provider_request_id     VARCHAR(120),
    provider_ref            VARCHAR(120),
    provider_journal_id     VARCHAR(120),
    provider_response       JSONB,
    provider_error_code     VARCHAR(80),
    provider_error_message  TEXT,
    idempotency_key_id      UUID REFERENCES idempotency_keys(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE payment_transactions
ADD CONSTRAINT chk_payment_transactions_type
CHECK (type IN ('AUTHORIZE', 'CAPTURE', 'VOID', 'REFUND'));

ALTER TABLE payment_transactions
ADD CONSTRAINT chk_payment_transactions_status
CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'COMPENSATION_REQUIRED'));

ALTER TABLE payment_transactions
ADD CONSTRAINT chk_payment_transactions_provider
CHECK (provider IN ('VIETQR_MANUAL', 'COREBANK', 'STUB'));

ALTER TABLE payment_transactions
ADD CONSTRAINT chk_payment_transactions_amount
CHECK (amount >= 0);

CREATE INDEX idx_payment_banks_active_display_order
    ON payment_banks(active, display_order, short_name);

CREATE INDEX idx_booking_payments_booking_status
    ON booking_payments(booking_id, status);

CREATE INDEX idx_payment_transactions_booking_payment_created
    ON payment_transactions(booking_payment_id, created_at);

CREATE INDEX idx_payment_transactions_booking_type_created
    ON payment_transactions(booking_id, type, created_at);

INSERT INTO payment_banks (
    id, code, bin, short_name, full_name, logo_url, country_code,
    payment_method, provider, active, display_order, metadata, created_at, updated_at
) VALUES
    ('00000000-0000-0000-0000-000000000101', 'VCB', NULL, 'Vietcombank',
        'Joint Stock Commercial Bank for Foreign Trade of Vietnam', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 10, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000102', 'BIDV', NULL, 'BIDV',
        'Bank for Investment and Development of Vietnam', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 20, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000103', 'CTG', NULL, 'VietinBank',
        'Vietnam Joint Stock Commercial Bank for Industry and Trade', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 30, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000104', 'AGRIBANK', NULL, 'Agribank',
        'Vietnam Bank for Agriculture and Rural Development', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 40, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000105', 'TCB', NULL, 'Techcombank',
        'Vietnam Technological and Commercial Joint Stock Bank', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 50, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000106', 'MB', NULL, 'MB Bank',
        'Military Commercial Joint Stock Bank', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 60, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000107', 'VPB', NULL, 'VPBank',
        'Vietnam Prosperity Joint Stock Commercial Bank', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 70, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000108', 'ACB', NULL, 'ACB',
        'Asia Commercial Joint Stock Bank', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 80, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000109', 'TPB', NULL, 'TPBank',
        'Tien Phong Commercial Joint Stock Bank', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 90, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-00000000010a', 'VIB', NULL, 'VIB',
        'Vietnam International Commercial Joint Stock Bank', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 100, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-00000000010b', 'HDB', NULL, 'HDBank',
        'Ho Chi Minh City Development Joint Stock Commercial Bank', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 110, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-00000000010c', 'SHB', NULL, 'SHB',
        'Saigon - Hanoi Commercial Joint Stock Bank', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 120, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-00000000010d', 'MSB', NULL, 'MSB',
        'Vietnam Maritime Commercial Joint Stock Bank', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 130, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-00000000010e', 'OCB', NULL, 'OCB',
        'Orient Commercial Joint Stock Bank', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 140, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-00000000010f', 'STB', NULL, 'Sacombank',
        'Saigon Thuong Tin Commercial Joint Stock Bank', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 150, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000110', 'EIB', NULL, 'Eximbank',
        'Vietnam Export Import Commercial Joint Stock Bank', NULL, 'VN',
        'BANK_TRANSFER_QR', 'VIETQR_MANUAL', TRUE, 160, NULL, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000111', 'COREBANK', NULL, 'CoreBank Demo Bank',
        'CoreBank Demo Bank', NULL, 'VN',
        'COREBANK_TRANSFER', 'COREBANK', TRUE, 1000, NULL, NOW(), NOW());
