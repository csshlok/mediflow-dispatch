package org.example.shared.events;

import java.util.UUID;

public record AmbulanceLocationUpdatedEvent(
        UUID ambulanceId,
        double latitude,
        double longitude,
        String timestamp
) {}
