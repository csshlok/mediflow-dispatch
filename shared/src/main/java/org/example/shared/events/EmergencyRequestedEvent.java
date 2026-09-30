package org.example.shared.events;

import org.example.shared.enums.Severity;

import java.time.Instant;
import java.util.UUID;

public record EmergencyRequestedEvent(
        UUID eventId,
        Instant createdAt,
        UUID emergencyId,
        String patientId,
        Severity severity,
        double latitude,
        double longitude
) {}
