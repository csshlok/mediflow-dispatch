package org.example.shared.dto;

import java.util.UUID;

public record CaseDTO(
        UUID caseId,
        UUID emergencyId,
        UUID ambulanceId,
        UUID hospitalId
) {}