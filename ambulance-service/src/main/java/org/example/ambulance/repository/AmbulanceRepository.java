package org.example.ambulance.repository;

import org.example.ambulance.entity.Ambulance;
import org.example.shared.enums.AmbulanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AmbulanceRepository extends JpaRepository<Ambulance, UUID> {
    List<Ambulance> findByStatus(AmbulanceStatus status);

    Optional<Ambulance> findByRegistrationNumber(String registrationNumber);
}