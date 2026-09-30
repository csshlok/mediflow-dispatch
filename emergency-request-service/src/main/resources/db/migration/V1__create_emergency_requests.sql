CREATE TABLE IF NOT EXISTS emergency_requests (
    id UUID PRIMARY KEY,
    patient_id VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    status VARCHAR(50) NOT NULL
);
