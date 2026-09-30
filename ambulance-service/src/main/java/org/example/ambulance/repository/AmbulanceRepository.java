package org.example.ambulance.repository;

import org.example.ambulance.entity.Ambulance;
import org.example.shared.enums.AmbulanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AmbulanceRepository extends JpaRepository<Ambulance, UUID> {
    List<Ambulance> findByStatus(AmbulanceStatus status);

    Optional<Ambulance> findByRegistrationNumber(String registrationNumber);

    // Atomic AVAILABLE -> RESERVED; returns 0 when another emergency got there first
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Ambulance a
               set a.status = org.example.shared.enums.AmbulanceStatus.RESERVED,
                   a.assignedEmergencyId = :emergencyId,
                   a.version = a.version + 1
             where a.id = :id
               and a.status = org.example.shared.enums.AmbulanceStatus.AVAILABLE
            """)
    int reserveIfAvailable(@Param("id") UUID id, @Param("emergencyId") UUID emergencyId);

    // Atomic state change for an ambulance held by the given emergency; returns 0 if the state or holder differ
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Ambulance a
               set a.status = :to,
                   a.assignedEmergencyId = :newEmergencyId,
                   a.version = a.version + 1
             where a.id = :id
               and a.status = :from
               and a.assignedEmergencyId = :emergencyId
            """)
    int transitionHeldBy(@Param("id") UUID id,
                         @Param("emergencyId") UUID emergencyId,
                         @Param("from") AmbulanceStatus from,
                         @Param("to") AmbulanceStatus to,
                         @Param("newEmergencyId") UUID newEmergencyId);
}
