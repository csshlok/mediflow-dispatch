package org.example.matching.repository;

import org.example.matching.entity.DispatchSaga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaRepository extends JpaRepository<DispatchSaga, UUID> {
    Optional<DispatchSaga> findByEmergencyId(String emergencyId);
}