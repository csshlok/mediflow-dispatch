package org.example.hospital.service;

import java.util.UUID;

public class HospitalNotFoundException extends RuntimeException {
    public HospitalNotFoundException(UUID id) {
        super("Hospital not found: " + id);
    }
}
