-- V23__fix_vehicle_category_enum_and_city.sql
-- Align DB constraints with Java enums and entity definitions.
--
-- 1. Add ECONOMY to vehicles.category check constraint (Java enum has it, DB did not).
-- 2. Ensure city NOT NULL is declared at entity level (already NOT NULL in DB from V8).

-- 1. Widen category check constraint to include ECONOMY
ALTER TABLE vehicles
DROP CONSTRAINT chk_vehicles_category;

ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_category
CHECK (category IN ('SEDAN', 'SUV', 'HATCHBACK', 'MPV', 'PICKUP', 'VAN', 'LUXURY', 'SPORTS', 'ECONOMY'));

-- 2. city is already NOT NULL in DB (V8). No DB change needed.
-- Entity @Column(nullable = false) will be updated in Java code.