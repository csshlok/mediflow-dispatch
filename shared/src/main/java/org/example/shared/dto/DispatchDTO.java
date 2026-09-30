package org.example.shared.dto;

import java.util.UUID;

public record DispatchDTO(
        UUID emergencyId,
        UUID ambulanceId
) {
}
