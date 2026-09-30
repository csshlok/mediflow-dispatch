package org.example.ambulance.repository;

import org.example.ambulance.entity.IdempotentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotentRequestRepository extends JpaRepository<IdempotentRequest, String> {
}