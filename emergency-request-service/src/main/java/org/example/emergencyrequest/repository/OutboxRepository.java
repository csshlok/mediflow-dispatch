package org.example.emergencyrequest.repository;

import org.example.emergencyrequest.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(String status);
}