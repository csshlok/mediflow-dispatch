package org.example.matching.repository;

import org.example.matching.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    // The background poller will use this to find unsent messages
    List<OutboxEvent> findByStatus(String status);
}