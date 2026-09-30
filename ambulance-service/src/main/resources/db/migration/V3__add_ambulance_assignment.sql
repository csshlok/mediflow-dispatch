-- Tracks which emergency currently holds the ambulance so reservation, pickup and delivery
-- can be checked against it, and adds the optimistic-lock column the entity expects.
ALTER TABLE ambulances
    ADD COLUMN IF NOT EXISTS assigned_emergency_id UUID,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS ux_ambulances_registration_number ON ambulances (registration_number);
