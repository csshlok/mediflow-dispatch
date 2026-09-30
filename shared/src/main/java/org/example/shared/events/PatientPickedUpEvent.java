package org.example.shared.events;

import java.time.Instant;
import java.util.UUID;

public record PatientPickedUpEvent(
        UUID eventId,
        Instant pickedUpAt,
        UUID emergencyId,
        UUID ambulanceId
) {}