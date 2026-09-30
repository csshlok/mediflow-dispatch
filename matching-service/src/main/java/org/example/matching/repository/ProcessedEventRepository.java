package org.example.matching.repository;

import org.example.matching.entity.ProcessedEvent;
import org.example.matching.entity.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}