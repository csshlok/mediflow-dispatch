package org.example.case_.repository;

import org.example.case_.entity.EmergencyCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<EmergencyCase, UUID> {
    Optional<EmergencyCase> findByEmergencyId(UUID emergencyId);
}
