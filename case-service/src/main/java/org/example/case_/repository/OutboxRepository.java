package org.example.case_.repository;

import org.example.case_.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    // The background poller will use this to grab unsent events
    List<OutboxEvent> findByStatus(String status);
}