package org.example.case_.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.example.case_.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    // Oldest pending events first, locked with FOR UPDATE SKIP LOCKED so parallel replicas never publish the same row
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(String status);
}
