package org.example.shared.events;

import java.time.Instant;
import java.util.UUID;

public record DispatchAssignedEvent(
        UUID eventId,
        Instant createdAt,
        UUID emergencyId,
        UUID ambulanceId,
        UUID hospitalId
) {}