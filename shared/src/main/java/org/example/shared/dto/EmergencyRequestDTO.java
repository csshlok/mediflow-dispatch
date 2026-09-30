package org.example.shared.dto;

import org.example.shared.enums.Severity;

public record EmergencyRequestDTO(
        String patientId,
        Severity severity,
        double latitude,
        double longitude
){}

