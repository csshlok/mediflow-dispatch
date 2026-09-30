package org.example.shared.dto;

import org.example.shared.enums.AmbulanceStatus;

import java.util.UUID;

public record Ambulance(
        UUID ambulanceId,
        AmbulanceStatus status,
        String registrationNumber,
        String capabilities,// e.g., "ALS, Ventilator, Defibrillator"
        String crewInfo    // e.g., "2 Paramedics, 1 Driver"
) {}

