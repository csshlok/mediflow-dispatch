package org.example.hospital.repository;

import org.example.hospital.entity.IdempotentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotentRequestRepository extends JpaRepository<IdempotentRequest, String> {
}