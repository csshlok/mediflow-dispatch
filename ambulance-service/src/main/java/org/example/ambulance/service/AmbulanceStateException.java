package org.example.ambulance.service;

// The ambulance exists but is not in the state (or held by the emergency) the operation requires
public class AmbulanceStateException extends RuntimeException {
    public AmbulanceStateException(String message) {
        super(message);
    }
}
