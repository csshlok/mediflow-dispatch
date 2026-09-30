package org.example.ambulance.repository;

import org.example.ambulance.entity.ProcessedEvent;
import org.example.ambulance.entity.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}