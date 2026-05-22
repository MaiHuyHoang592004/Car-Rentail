-- Add city column to vehicles table
ALTER TABLE vehicles ADD COLUMN city VARCHAR(100);

-- Backfill empty city with a placeholder so existing rows remain valid
UPDATE vehicles SET city = 'Unknown' WHERE city IS NULL;

-- Make column NOT NULL after backfill (optional: keeps schema clean)
ALTER TABLE vehicles ALTER COLUMN city SET NOT NULL;
