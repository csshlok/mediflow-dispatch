package org.example.emergencyrequest.repository;

import org.example.emergencyrequest.entity.EmergencyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EmergencyRequestRepository extends JpaRepository<EmergencyRequest, UUID> {
}
