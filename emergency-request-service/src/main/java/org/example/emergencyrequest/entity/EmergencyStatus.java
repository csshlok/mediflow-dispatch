package org.example.emergencyrequest.entity;

// Lifecycle of an emergency. Status only ever moves to a higher rank, so replayed or late events are harmless.
public enum EmergencyStatus {
    PENDING_MATCH(0),
    DISPATCH_FAILED(1),   // retries exhausted; a DLQ re-drive can still move it to DISPATCHED
    DISPATCHED(2),
    PATIENT_PICKED_UP(3),
    DELIVERED(4);

    private final int rank;

    EmergencyStatus(int rank) {
        this.rank = rank;
    }

    public boolean isAfter(EmergencyStatus other) {
        return rank > other.rank;
    }
}
