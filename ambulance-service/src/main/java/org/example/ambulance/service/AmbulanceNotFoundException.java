package org.example.ambulance.service;

import java.util.UUID;

public class AmbulanceNotFoundException extends RuntimeException {
    public AmbulanceNotFoundException(UUID id) {
        super("Ambulance not found: " + id);
    }
}
