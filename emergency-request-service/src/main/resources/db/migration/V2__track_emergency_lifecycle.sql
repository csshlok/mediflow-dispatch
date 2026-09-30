-- Emergency lifecycle tracking: which ambulance and hospital were assigned, and when the status last changed
ALTER TABLE emergency_requests
    ADD COLUMN IF NOT EXISTS ambulance_id UUID,
    ADD COLUMN IF NOT EXISTS hospital_id UUID,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS ix_emergency_requests_status ON emergency_requests (status);
