package org.example.shared.enums;

public enum SagaState {
    STARTED,             // Saga created, nothing reserved yet
    AMBULANCE_RESERVED,  // Ambulance locked in, attempting hospital
    HOSPITAL_RESERVED,   // Both locked in, preparing final dispatch
    COMPLETED,           // Successfully pushed to Outbox
    FAILED,              // Attempt failed and compensation did not finish; the recovery job retries it
    COMPENSATED          // Attempt failed and every reservation was released; the event will be retried
}