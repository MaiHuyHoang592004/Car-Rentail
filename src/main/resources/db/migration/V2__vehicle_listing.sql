-- V2__vehicle_listing.sql
-- Phase 3: Vehicle and Listing tables.

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- vehicles
CREATE TABLE vehicles (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    host_id                     UUID NOT NULL,
    category                    VARCHAR(30) NOT NULL,
    make                        VARCHAR(50) NOT NULL,
    model                       VARCHAR(50) NOT NULL,
    manufacture_year INTEGER NOT NULL,
    plate_number_encrypted      TEXT,
    plate_number_hash           VARCHAR(128),
    vin_encrypted               TEXT,
    vin_hash                    VARCHAR(128),
    transmission                VARCHAR(20) NOT NULL,
    fuel_type                   VARCHAR(20) NOT NULL,
    seats                       INTEGER NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_seats CHECK (seats > 0);

ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_manufacture_year CHECK (manufacture_year >= 1990);

ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_status CHECK (status IN ('DRAFT', 'ACTIVE', 'MAINTENANCE', 'SUSPENDED', 'ARCHIVED'));

ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_category CHECK (category IN ('SEDAN', 'SUV', 'HATCHBACK', 'MPV', 'PICKUP', 'VAN', 'LUXURY', 'SPORTS'));

ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_transmission CHECK (transmission IN ('AUTO', 'MANUAL'));

ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_fuel_type CHECK (fuel_type IN ('GASOLINE', 'DIESEL', 'EV', 'HYBRID'));

-- listings
CREATE TABLE listings (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    vehicle_id                  UUID NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
    host_id                     UUID NOT NULL,
    title                       VARCHAR(200) NOT NULL,
    description                 TEXT,
    city                        VARCHAR(100) NOT NULL,
    address                     TEXT,
    latitude                    DECIMAL(10, 7),
    longitude                   DECIMAL(10, 7),
    base_price_per_day          DECIMAL(12, 2) NOT NULL,
    currency                    VARCHAR(3) NOT NULL DEFAULT 'VND',
    daily_km_limit              INTEGER,
    instant_book                 BOOLEAN NOT NULL DEFAULT false,
    cancellation_policy          VARCHAR(20) NOT NULL DEFAULT 'FLEXIBLE',
    status                      VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE listings
ADD CONSTRAINT chk_listings_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED', 'ARCHIVED'));

ALTER TABLE listings
ADD CONSTRAINT chk_listings_cancellation_policy CHECK (cancellation_policy IN ('FLEXIBLE', 'MODERATE', 'STRICT'));

-- extras
CREATE TABLE extras (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    listing_id                  UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    name                        VARCHAR(100) NOT NULL,
    pricing_type                VARCHAR(20) NOT NULL,
    price                       DECIMAL(12, 2) NOT NULL,
    active                      BOOLEAN NOT NULL DEFAULT true,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE extras
ADD CONSTRAINT chk_extras_pricing_type CHECK (pricing_type IN ('PER_DAY', 'PER_TRIP'));

ALTER TABLE extras
ADD CONSTRAINT chk_extras_active CHECK (active IN (true, false));

-- Indexes
CREATE INDEX idx_vehicles_host_status ON vehicles(host_id, status);
CREATE INDEX idx_listings_status_city_price ON listings(status, city, base_price_per_day);
CREATE INDEX idx_listings_host_status ON listings(host_id, status);
CREATE INDEX idx_listings_vehicle_status ON listings(vehicle_id, status);

-- Partial unique index: only one ACTIVE listing per vehicle
CREATE UNIQUE INDEX uq_listings_one_active_per_vehicle
    ON listings(vehicle_id)
    WHERE status = 'ACTIVE';
