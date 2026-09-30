package org.example.shared.dto;

import java.util.UUID;

public record HospitalDTO(
        UUID hospitalId,
        String hospitalName,
        int availableBeds,
        double latitude,
        double longitude
) {}