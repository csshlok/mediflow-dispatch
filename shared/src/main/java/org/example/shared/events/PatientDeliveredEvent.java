package org.example.shared.events;

import java.time.Instant;
import java.util.UUID;

public record PatientDeliveredEvent(
        UUID eventId,
        Instant deliveredAt,
        UUID emergencyId,
        UUID hospitalId
) {}