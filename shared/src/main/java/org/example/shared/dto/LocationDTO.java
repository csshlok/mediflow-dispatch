package org.example.shared.dto;

import java.util.UUID;

public record LocationDTO(
        UUID ambulanceId,
        double latitude,
        double longitude
) {}