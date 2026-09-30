package org.example.case_.repository;

import org.example.case_.entity.ProcessedEvent;
import org.example.case_.entity.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}