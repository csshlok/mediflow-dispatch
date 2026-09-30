package org.example.emergencyrequest.repository;

import jakarta.persistence.LockModeType;
import org.example.emergencyrequest.entity.EmergencyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmergencyRequestRepository extends JpaRepository<EmergencyRequest, UUID> {

    List<EmergencyRequest> findByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmergencyRequest e where e.id = :id")
    Optional<EmergencyRequest> findByIdForUpdate(@Param("id") UUID id);
}
