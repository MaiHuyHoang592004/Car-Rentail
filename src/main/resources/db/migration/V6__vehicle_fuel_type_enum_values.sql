-- V6__vehicle_fuel_type_enum_values.sql
-- Keep the vehicles.fuel_type check constraint aligned with the FuelType enum.

ALTER TABLE vehicles
DROP CONSTRAINT chk_vehicles_fuel_type;

ALTER TABLE vehicles
ADD CONSTRAINT chk_vehicles_fuel_type
CHECK (fuel_type IN ('PETROL', 'DIESEL', 'ELECTRIC', 'HYBRID', 'LPG', 'GASOLINE', 'EV'));
