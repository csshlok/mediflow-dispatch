package org.example.notification.repository;

import org.example.notification.entity.ProcessedEvent;
import org.example.notification.entity.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}