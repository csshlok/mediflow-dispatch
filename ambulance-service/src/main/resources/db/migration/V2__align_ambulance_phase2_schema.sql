ALTER TABLE ambulances
    DROP COLUMN IF EXISTS name,
    DROP COLUMN IF EXISTS latitude,
    DROP COLUMN IF EXISTS longitude;

ALTER TABLE ambulances
    ADD COLUMN IF NOT EXISTS registration_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS capabilities VARCHAR(255),
    ADD COLUMN IF NOT EXISTS crew_info VARCHAR(255);

UPDATE ambulances
SET registration_number = COALESCE(registration_number, 'UNKNOWN'),
    capabilities = COALESCE(capabilities, 'UNKNOWN'),
    crew_info = COALESCE(crew_info, 'UNKNOWN');

ALTER TABLE ambulances
    ALTER COLUMN registration_number SET NOT NULL,
    ALTER COLUMN capabilities SET NOT NULL,
    ALTER COLUMN crew_info SET NOT NULL;
