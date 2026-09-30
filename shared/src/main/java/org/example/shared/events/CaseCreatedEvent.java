package org.example.shared.events;

import java.time.Instant;
import java.util.UUID;

public record CaseCreatedEvent(
        UUID eventId,
        Instant createdAt,
        UUID caseId,
        UUID emergencyId,
        UUID ambulanceId,
        UUID hospitalId,
        String hospitalName
) {}