package org.example.case_.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Id
    @Column(name = "consumer_name", length = 100)
    private String consumerName;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent() {}

    public ProcessedEvent(String eventId, String consumerName, Instant processedAt) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.processedAt = processedAt;
    }
}