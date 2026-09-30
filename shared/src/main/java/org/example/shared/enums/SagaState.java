package org.example.shared.enums;

public enum SagaState {
    STARTED,             // Saga created, nothing reserved yet
    AMBULANCE_RESERVED,  // Ambulance locked in, attempting hospital
    HOSPITAL_RESERVED,   // Both locked in, preparing final dispatch
    COMPLETED,           // Successfully pushed to Outbox
    FAILED,              // Network failed entirely
    COMPENSATED          // Ambulance was successfully released after a failure
}