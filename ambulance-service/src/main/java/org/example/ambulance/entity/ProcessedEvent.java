package org.example.ambulance.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class) // Links this entity to the composite key class above
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Id
    @Column(name = "consumer_name", nullable = false)
    private String consumerName;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent() {}

    // Matches the exact constructor you used in the consumer
    public ProcessedEvent(String eventId, String consumerName, Instant processedAt) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}